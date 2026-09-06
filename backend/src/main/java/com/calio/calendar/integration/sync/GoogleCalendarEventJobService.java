package com.calio.calendar.integration.sync;

import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventWriteRequest;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState;
import com.calio.calendar.integration.connection.service.GoogleCalendarAccessTokenService;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionQueryService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobHandler;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class GoogleCalendarEventJobService implements GoogleOperationJobHandler {

    private final GoogleCalendarConnectionQueryService connectionQueryService;
    private final GoogleCalendarEventMappingQueryService mappingQueryService;
    private final GoogleCalendarEventMappingCommandService mappingCommandService;
    private final GoogleCalendarAccessTokenService accessTokenService;
    private final GoogleCalendarEventsClient eventsClient;
    private final ObjectMapper objectMapper;
    private final GoogleOperationJobService jobService;
    private final TransactionTemplate transactionTemplate;

    public GoogleCalendarEventJobService(
            GoogleCalendarConnectionQueryService connectionQueryService,
            GoogleCalendarEventMappingQueryService mappingQueryService,
            GoogleCalendarEventMappingCommandService mappingCommandService,
            GoogleCalendarAccessTokenService accessTokenService,
            GoogleCalendarEventsClient eventsClient,
            ObjectMapper objectMapper,
            GoogleOperationJobService jobService,
            TransactionTemplate transactionTemplate
    ) {
        this.connectionQueryService = connectionQueryService;
        this.mappingQueryService = mappingQueryService;
        this.mappingCommandService = mappingCommandService;
        this.accessTokenService = accessTokenService;
        this.eventsClient = eventsClient;
        this.objectMapper = objectMapper;
        this.jobService = jobService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public Class<GoogleCalendarEventJob> jobType() {
        return GoogleCalendarEventJob.class;
    }

    @Override
    public void execute(GoogleOperationJob job, String workerToken) {
        apply((GoogleCalendarEventJob) job, workerToken);
    }

    public void apply(GoogleCalendarEventJob job, String workerToken) {
        switch (job.getKind()) {
            case CREATE -> createEvent(job, workerToken);
            case UPDATE -> updateEvent(job, workerToken);
            case DELETE -> deleteEvent(job, workerToken);
            default -> throw new IllegalArgumentException("Unsupported Google Event job kind: " + job.getKind());
        }
    }

    private void createEvent(GoogleCalendarEventJob job, String workerToken) {
        GoogleEventJobPayload eventSnapshot = readEventSnapshot(job);
        List<EventMappingSnapshot> mappings = loadMappingSnapshots(job);
        Long targetConnectionId = transactionTemplate.execute(
                status -> findCreationTarget(job, mappings)
        );
        List<MappingExecutionResult> mappingResults = patchMappedEvents(eventSnapshot, mappings);
        GoogleCalendarEventResponse createdEvent = null;
        if (mappingOutcome(mappingResults) == MappingOutcome.APPLIED && targetConnectionId != null) {
            createdEvent = insertEvent(job, eventSnapshot, targetConnectionId);
        }
        GoogleCalendarEventResponse result = createdEvent;
        transactionTemplate.executeWithoutResult(status ->
                completeCreate(job, workerToken, mappingResults, targetConnectionId, result));
    }

    private void updateEvent(GoogleCalendarEventJob job, String workerToken) {
        GoogleEventJobPayload eventSnapshot = readEventSnapshot(job);
        List<EventMappingSnapshot> mappings = loadMappingSnapshots(job);
        List<MappingExecutionResult> mappingResults = patchMappedEvents(eventSnapshot, mappings);
        transactionTemplate.executeWithoutResult(status ->
                completeUpdate(job, workerToken, mappingResults));
    }

    private void deleteEvent(GoogleCalendarEventJob job, String workerToken) {
        List<EventMappingSnapshot> mappings = loadMappingSnapshots(job);
        List<MappingExecutionResult> mappingResults = deleteMappedEvents(mappings);
        transactionTemplate.executeWithoutResult(status ->
                completeDelete(job, workerToken, mappingResults));
    }

    private List<EventMappingSnapshot> loadMappingSnapshots(GoogleCalendarEventJob job) {
        return Objects.requireNonNull(transactionTemplate.execute(status ->
                mappingQueryService.listEventMappingsForEvent(
                                job.getIntegrationId(), job.getEventId())
                        .stream()
                        .map(EventMappingSnapshot::from)
                        .toList()
        ));
    }

    private Long findCreationTarget(
            GoogleCalendarEventJob job,
            List<EventMappingSnapshot> mappings
    ) {
        if (mappings.stream().anyMatch(EventMappingSnapshot::conflicted)) {
            return null;
        }
        return connectionQueryService.listConnections(job.getIntegrationId()).stream()
                .filter(GoogleCalendarConnection::isConnected)
                .filter(connection -> hasNoMapping(mappings, connection.getId()))
                .map(GoogleCalendarConnection::getId)
                .findFirst()
                .orElse(null);
    }

    private boolean hasNoMapping(List<EventMappingSnapshot> mappings, Long connectionId) {
        return mappings.stream().noneMatch(mapping -> mapping.connectionId().equals(connectionId));
    }

    private List<MappingExecutionResult> patchMappedEvents(
            GoogleEventJobPayload eventSnapshot,
            List<EventMappingSnapshot> mappings
    ) {
        return mappings.stream()
                .map(mapping -> patchMappedEvent(eventSnapshot, mapping))
                .toList();
    }

    private MappingExecutionResult patchMappedEvent(
            GoogleEventJobPayload eventSnapshot,
            EventMappingSnapshot mapping
    ) {
        if (mapping.conflicted()) {
            return MappingExecutionResult.alreadyConflicted(mapping.mappingId());
        }
        if (mapping.connectionState() != GoogleCalendarConnectionState.CONNECTED) {
            return MappingExecutionResult.markedLocalChange(mapping.mappingId());
        }
        String accessToken = accessTokenService.getAccessToken(mapping.connectionId());
        GoogleCalendarEventResponse providerEvent = eventsClient
                .getEvent(accessToken, mapping.externalEventId()).orElse(null);
        if (providerEvent == null || !mapping.providerEtag().equals(providerEvent.etag())) {
            return MappingExecutionResult.conflictDetected(mapping.mappingId());
        }
        GoogleCalendarEventResponse updatedEvent = eventsClient.patchEvent(
                accessToken,
                mapping.externalEventId(),
                GoogleCalendarEventWriteRequest.from(eventSnapshot)
        );
        return MappingExecutionResult.updated(
                mapping.mappingId(), mapping.providerEtag(), updatedEvent.etag());
    }

    private List<MappingExecutionResult> deleteMappedEvents(List<EventMappingSnapshot> mappings) {
        return mappings.stream()
                .map(this::deleteMappedEvent)
                .toList();
    }

    private MappingExecutionResult deleteMappedEvent(EventMappingSnapshot mapping) {
        if (mapping.conflicted()) {
            return MappingExecutionResult.alreadyConflicted(mapping.mappingId());
        }
        if (mapping.connectionState() != GoogleCalendarConnectionState.CONNECTED) {
            return MappingExecutionResult.markedLocalChange(mapping.mappingId());
        }
        String accessToken = accessTokenService.getAccessToken(mapping.connectionId());
        eventsClient.deleteEvent(accessToken, mapping.externalEventId());
        return MappingExecutionResult.applied(mapping.mappingId());
    }

    private GoogleCalendarEventResponse insertEvent(
            GoogleCalendarEventJob job,
            GoogleEventJobPayload eventSnapshot,
            Long targetConnectionId
    ) {
        String accessToken = accessTokenService.getAccessToken(targetConnectionId);
        return eventsClient.insertEvent(
                accessToken,
                GoogleCalendarEventWriteRequest.forCreate(eventSnapshot, job.getProviderIdentity())
        );
    }

    private void completeCreate(
            GoogleCalendarEventJob job,
            String workerToken,
            List<MappingExecutionResult> mappingResults,
            Long targetConnectionId,
            GoogleCalendarEventResponse createdEvent
    ) {
        Map<Long, GoogleCalendarEventMapping> mappingsById = findMappingsById(job);
        MappingOutcome outcome = applyMappingResults(mappingResults, mappingsById);
        if (completeConflictOrSkip(job, workerToken, outcome)) {
            return;
        }
        createEventMapping(job, targetConnectionId, createdEvent, mappingsById);
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private void completeUpdate(
            GoogleCalendarEventJob job,
            String workerToken,
            List<MappingExecutionResult> mappingResults
    ) {
        MappingOutcome outcome = applyMappingResults(mappingResults, findMappingsById(job));
        if (completeConflictOrSkip(job, workerToken, outcome)) {
            return;
        }
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private void completeDelete(
            GoogleCalendarEventJob job,
            String workerToken,
            List<MappingExecutionResult> mappingResults
    ) {
        MappingOutcome outcome = applyMappingResults(mappingResults, findMappingsById(job));
        if (completeConflictOrSkip(job, workerToken, outcome)) {
            return;
        }
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private boolean completeConflictOrSkip(
            GoogleCalendarEventJob job,
            String workerToken,
            MappingOutcome outcome
    ) {
        if (outcome == MappingOutcome.ALREADY_CONFLICTED) {
            jobService.skipConflictedScope(job.getId(), job.getAccountId(), workerToken);
            return true;
        }
        if (outcome == MappingOutcome.CONFLICT_DETECTED) {
            jobService.recordSyncConflict(job.getId(), job.getAccountId(), workerToken);
            jobService.completeSyncRun(job.getId(), job.getAccountId(), workerToken);
            return true;
        }
        return false;
    }

    private MappingOutcome applyMappingResults(
            List<MappingExecutionResult> mappingResults,
            Map<Long, GoogleCalendarEventMapping> mappingsById
    ) {
        MappingOutcome outcome = MappingOutcome.APPLIED;
        for (MappingExecutionResult result : mappingResults) {
            GoogleCalendarEventMapping mapping = mappingsById.get(result.mappingId());
            if (mapping != null) {
                outcome = outcome.merge(applyMappingResult(mapping, result));
            }
        }
        return outcome;
    }

    private Map<Long, GoogleCalendarEventMapping> findMappingsById(GoogleCalendarEventJob job) {
        Map<Long, GoogleCalendarEventMapping> mappingsById = new HashMap<>();
        mappingQueryService.listEventMappingsForEvent(
                        job.getIntegrationId(), job.getEventId())
                .forEach(mapping -> mappingsById.put(mapping.getId(), mapping));
        return mappingsById;
    }

    private MappingOutcome applyMappingResult(
            GoogleCalendarEventMapping mapping,
            MappingExecutionResult result
    ) {
        if (mapping.isConflicted() && result.outcome() == MappingOutcome.APPLIED) {
            return MappingOutcome.ALREADY_CONFLICTED;
        }
        if (result.outcome() == MappingOutcome.CONFLICT_DETECTED) {
            mapping.markConflicted();
            return MappingOutcome.CONFLICT_DETECTED;
        }
        if (result.localChangeDetected()) {
            mapping.markLocalChanged();
        }
        if (result.updatedProviderEtag() != null) {
            if (!mapping.getProviderEtag().equals(result.expectedProviderEtag())) {
                mapping.markConflicted();
                return MappingOutcome.CONFLICT_DETECTED;
            }
            mapping.updateProviderEtag(result.updatedProviderEtag());
        }
        return result.outcome();
    }

    private void createEventMapping(
            GoogleCalendarEventJob job,
            Long targetConnectionId,
            GoogleCalendarEventResponse createdEvent,
            Map<Long, GoogleCalendarEventMapping> mappingsById
    ) {
        if (createdEvent == null || targetConnectionId == null) {
            return;
        }
        if (mappingsById.values().stream()
                .anyMatch(mapping -> mapping.getConnection().getId().equals(targetConnectionId))) {
            return;
        }
        GoogleCalendarConnection connection = connectionQueryService.listConnections(job.getIntegrationId()).stream()
                .filter(candidate -> candidate.getId().equals(targetConnectionId))
                .findFirst()
                .orElse(null);
        if (connection != null) {
            mappingCommandService.createEventMapping(new GoogleCalendarEventMapping(
                    connection,
                    job.getEventId(),
                    createdEvent.id(),
                    createdEvent.etag()
            ));
        }
    }

    private MappingOutcome mappingOutcome(List<MappingExecutionResult> mappingResults) {
        return mappingResults.stream()
                .map(MappingExecutionResult::outcome)
                .reduce(MappingOutcome.APPLIED, MappingOutcome::merge);
    }

    private GoogleEventJobPayload readEventSnapshot(GoogleCalendarEventJob job) {
        try {
            return objectMapper.readValue(job.getTargetPayload(), GoogleEventJobPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Google Event job payload cannot be decoded", exception);
        }
    }

    private record EventMappingSnapshot(
            Long mappingId,
            Long connectionId,
            GoogleCalendarConnectionState connectionState,
            String externalEventId,
            String providerEtag,
            boolean conflicted
    ) {
        private static EventMappingSnapshot from(GoogleCalendarEventMapping mapping) {
            return new EventMappingSnapshot(
                    mapping.getId(),
                    mapping.getConnection().getId(),
                    mapping.getConnection().getState(),
                    mapping.getExternalEventId(),
                    mapping.getProviderEtag(),
                    mapping.isConflicted()
            );
        }
    }

    private record MappingExecutionResult(
            Long mappingId,
            MappingOutcome outcome,
            boolean localChangeDetected,
            String expectedProviderEtag,
            String updatedProviderEtag
    ) {
        private static MappingExecutionResult applied(Long mappingId) {
            return new MappingExecutionResult(mappingId, MappingOutcome.APPLIED, false, null, null);
        }

        private static MappingExecutionResult markedLocalChange(Long mappingId) {
            return new MappingExecutionResult(mappingId, MappingOutcome.APPLIED, true, null, null);
        }

        private static MappingExecutionResult updated(
                Long mappingId,
                String expectedProviderEtag,
                String updatedProviderEtag
        ) {
            return new MappingExecutionResult(
                    mappingId, MappingOutcome.APPLIED, false, expectedProviderEtag, updatedProviderEtag);
        }

        private static MappingExecutionResult conflictDetected(Long mappingId) {
            return new MappingExecutionResult(
                    mappingId, MappingOutcome.CONFLICT_DETECTED, false, null, null);
        }

        private static MappingExecutionResult alreadyConflicted(Long mappingId) {
            return new MappingExecutionResult(
                    mappingId, MappingOutcome.ALREADY_CONFLICTED, false, null, null);
        }
    }

    private enum MappingOutcome {
        APPLIED,
        CONFLICT_DETECTED,
        ALREADY_CONFLICTED;

        private MappingOutcome merge(MappingOutcome other) {
            if (this == ALREADY_CONFLICTED || other == ALREADY_CONFLICTED) {
                return ALREADY_CONFLICTED;
            }
            if (this == CONFLICT_DETECTED || other == CONFLICT_DETECTED) {
                return CONFLICT_DETECTED;
            }
            return APPLIED;
        }
    }
}
