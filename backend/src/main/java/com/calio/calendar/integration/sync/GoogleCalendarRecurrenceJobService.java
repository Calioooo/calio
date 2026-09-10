package com.calio.calendar.integration.sync;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventVersionConflictException;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventWriteRequest;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState;
import com.calio.calendar.integration.connection.service.GoogleCalendarAccessTokenService;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionQueryService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJob;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class GoogleCalendarRecurrenceJobService {
    private final GoogleCalendarConnectionQueryService connectionQueryService;
    private final GoogleCalendarRecurrenceMappingQueryService mappingQueryService;
    private final GoogleCalendarRecurrenceMappingCommandService mappingCommandService;
    private final GoogleCalendarAccessTokenService accessTokenService;
    private final GoogleCalendarEventsClient eventsClient;
    private final ObjectMapper objectMapper;
    private final GoogleOperationJobService jobService;
    private final TransactionTemplate transactionTemplate;

    public GoogleCalendarRecurrenceJobService(
            GoogleCalendarConnectionQueryService connectionQueryService,
            GoogleCalendarRecurrenceMappingQueryService mappingQueryService,
            GoogleCalendarRecurrenceMappingCommandService mappingCommandService,
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

    public void execute(GoogleCalendarRecurrenceJob job, String workerToken) {
        switch (job.getKind()) {
            case RECURRENCE_CREATE -> createRecurrence(job, workerToken);
            case RECURRENCE_UPDATE -> updateRecurrence(job, workerToken);
            case RECURRENCE_DELETE -> deleteRecurrence(job, workerToken);
            case OVERRIDE_UPSERT -> upsertOverride(job, workerToken);
            case OVERRIDE_DELETE -> deleteOverride(job, workerToken);
            default -> throw new IllegalArgumentException(
                    "Unsupported Google Recurrence Event job kind: " + job.getKind());
        }
    }

    private void createRecurrence(GoogleCalendarRecurrenceJob job, String workerToken) {
        GoogleRecurrenceJobPayload payload = readRecurrenceSnapshot(job);
        List<RecurrenceMappingSnapshot> mappings = loadRecurrenceMappingSnapshots(job);
        Long targetConnectionId = transactionTemplate.execute(
                status -> findCreationTarget(job, mappings)
        );
        List<MappingExecutionResult> mappingResults = patchMappedRecurrences(payload, mappings);
        GoogleCalendarEventResponse created = null;
        if (mappingOutcome(mappingResults) == MappingOutcome.APPLIED && targetConnectionId != null) {
            created = insertRecurrence(job, payload, targetConnectionId);
        }
        GoogleCalendarEventResponse createdEvent = created;
        transactionTemplate.executeWithoutResult(status -> completeRecurrenceCreate(
                job, workerToken, mappingResults, targetConnectionId, createdEvent));
    }

    private void updateRecurrence(GoogleCalendarRecurrenceJob job, String workerToken) {
        GoogleRecurrenceJobPayload payload = readRecurrenceSnapshot(job);
        List<RecurrenceMappingSnapshot> mappings = loadRecurrenceMappingSnapshots(job);
        List<MappingExecutionResult> mappingResults = patchMappedRecurrences(payload, mappings);
        transactionTemplate.executeWithoutResult(status ->
                completeRecurrenceUpdate(job, workerToken, mappingResults));
    }

    private void deleteRecurrence(GoogleCalendarRecurrenceJob job, String workerToken) {
        List<RecurrenceMappingSnapshot> mappings = loadRecurrenceMappingSnapshots(job);
        List<MappingExecutionResult> mappingResults = deleteMappedRecurrences(mappings);
        transactionTemplate.executeWithoutResult(status ->
                completeRecurrenceDelete(job, workerToken, mappingResults));
    }

    private Long findCreationTarget(
            GoogleCalendarRecurrenceJob job,
            List<RecurrenceMappingSnapshot> mappings
    ) {
        if (mappings.stream().anyMatch(RecurrenceMappingSnapshot::conflicted)) {
            return null;
        }
        return connectionQueryService.listConnections(job.getIntegrationId()).stream()
                .filter(GoogleCalendarConnection::isConnected)
                .filter(connection -> hasNoMapping(mappings, connection.getId()))
                .map(GoogleCalendarConnection::getId)
                .findFirst()
                .orElse(null);
    }

    private boolean hasNoMapping(List<RecurrenceMappingSnapshot> mappings, Long connectionId) {
        return mappings.stream().noneMatch(mapping -> mapping.connectionId().equals(connectionId));
    }

    private List<MappingExecutionResult> patchMappedRecurrences(
            GoogleRecurrenceJobPayload payload,
            List<RecurrenceMappingSnapshot> mappings
    ) {
        return mappings.stream()
                .map(mapping -> patchMappedRecurrence(mapping, payload))
                .toList();
    }

    private MappingExecutionResult patchMappedRecurrence(
            RecurrenceMappingSnapshot mapping,
            GoogleRecurrenceJobPayload payload
    ) {
        if (mapping.conflicted()) {
            return MappingExecutionResult.alreadyConflicted(mapping.mappingId());
        }
        if (mapping.state() != GoogleCalendarConnectionState.CONNECTED) {
            return MappingExecutionResult.localChanged(mapping.mappingId());
        }
        String token = accessTokenService.getAccessToken(mapping.connectionId());
        GoogleCalendarEventResponse current = eventsClient.getEvent(token, mapping.externalId()).orElse(null);
        if (current == null || !mapping.etag().equals(current.etag())) {
            return MappingExecutionResult.conflictDetected(mapping.mappingId());
        }
        try {
            GoogleCalendarEventResponse updated = eventsClient.patch(
                    token, mapping.externalId(), mapping.etag(),
                    GoogleCalendarEventWriteRequest.forRecurrenceUpdate(payload)
            );
            return MappingExecutionResult.updated(mapping.mappingId(), mapping.etag(), updated.etag());
        } catch (GoogleCalendarEventVersionConflictException exception) {
            return MappingExecutionResult.conflictDetected(mapping.mappingId());
        }
    }

    private List<MappingExecutionResult> deleteMappedRecurrences(
            List<RecurrenceMappingSnapshot> mappings
    ) {
        return mappings.stream()
                .map(this::deleteMappedRecurrence)
                .toList();
    }

    private MappingExecutionResult deleteMappedRecurrence(RecurrenceMappingSnapshot mapping) {
        if (mapping.conflicted()) {
            return MappingExecutionResult.alreadyConflicted(mapping.mappingId());
        }
        if (mapping.state() != GoogleCalendarConnectionState.CONNECTED) {
            return MappingExecutionResult.localChanged(mapping.mappingId());
        }
        try {
            eventsClient.delete(
                    accessTokenService.getAccessToken(mapping.connectionId()),
                    mapping.externalId(), mapping.etag()
            );
            return MappingExecutionResult.deleted(mapping.mappingId());
        } catch (GoogleCalendarEventVersionConflictException exception) {
            return MappingExecutionResult.conflictDetected(mapping.mappingId());
        }
    }

    private GoogleCalendarEventResponse insertRecurrence(
            GoogleCalendarRecurrenceJob job,
            GoogleRecurrenceJobPayload payload,
            Long targetConnectionId
    ) {
        return eventsClient.post(
                accessTokenService.getAccessToken(targetConnectionId),
                GoogleCalendarEventWriteRequest.forRecurrenceCreate(payload, job.getProviderIdentity())
        );
    }

    private void upsertOverride(GoogleCalendarRecurrenceJob job, String workerToken) {
        GoogleRecurrenceOverrideJobPayload payload = readRecurrenceOverrideSnapshot(job);
        List<OverrideExecutionResult> results = loadOverrideMappingScopes(job).stream()
                .map(scope -> applyOverride(job, scope, payload, false))
                .toList();
        transactionTemplate.executeWithoutResult(status -> completeOverride(job, workerToken, results));
    }

    private void deleteOverride(GoogleCalendarRecurrenceJob job, String workerToken) {
        List<OverrideExecutionResult> results = loadOverrideMappingScopes(job).stream()
                .map(scope -> applyOverride(job, scope, null, true))
                .toList();
        transactionTemplate.executeWithoutResult(status -> completeOverride(job, workerToken, results));
    }

    private OverrideExecutionResult applyOverride(
            GoogleCalendarRecurrenceJob job, OverrideMappingScope scope,
            GoogleRecurrenceOverrideJobPayload payload, boolean delete
    ) {
        RecurrenceMappingSnapshot master = scope.master();
        if (master.conflicted() || scope.overrideConflicted()) {
            return OverrideExecutionResult.alreadyConflicted(master.mappingId(), scope.overrideId());
        }
        if (master.state() != GoogleCalendarConnectionState.CONNECTED) {
            return scope.overrideId() == null ? OverrideExecutionResult.applied(master.mappingId(), null)
                    : OverrideExecutionResult.localChanged(master.mappingId(), scope.overrideId());
        }
        String token = accessTokenService.getAccessToken(master.connectionId());
        GoogleCalendarEventResponse providerMaster = eventsClient.getEvent(
                token, master.externalId()).orElse(null);
        if (providerMaster == null || !master.etag().equals(providerMaster.etag())) {
            return OverrideExecutionResult.masterConflict(master.mappingId(), scope.overrideId());
        }
        GoogleCalendarEventResponse occurrence;
        if (scope.overrideId() == null) {
            occurrence = eventsClient.getRecurrenceOccurrence(
                    token, master.externalId(), job.getOriginStartAt()).orElse(null);
            if (occurrence == null) {
                return OverrideExecutionResult.masterConflict(master.mappingId(), null);
            }
            if (occurrence.isCancelled()) {
                return OverrideExecutionResult.masterConflict(master.mappingId(), null);
            }
        } else {
            occurrence = eventsClient.getEvent(token, scope.externalId()).orElse(null);
            if (occurrence == null || !scope.etag().equals(occurrence.etag())) {
                return OverrideExecutionResult.overrideConflict(master.mappingId(), scope.overrideId());
            }
        }
        String expected = scope.overrideId() == null ? occurrence.etag() : scope.etag();
        try {
            if (delete) {
                eventsClient.delete(token, occurrence.id(), expected);
                return OverrideExecutionResult.deleted(master.mappingId(), scope.overrideId());
            }
            GoogleCalendarEventResponse updated = eventsClient.patch(
                    token, occurrence.id(), expected,
                    GoogleCalendarEventWriteRequest.forOverrideUpdate(payload)
            );
            return OverrideExecutionResult.updated(
                    master.mappingId(), scope.overrideId(),
                    occurrence.id(), expected, updated.etag()
            );
        } catch (GoogleCalendarEventVersionConflictException exception) {
            return OverrideExecutionResult.overrideConflict(
                    master.mappingId(), scope.overrideId(), occurrence.id(), expected);
        }
    }

    private List<RecurrenceMappingSnapshot> loadRecurrenceMappingSnapshots(
            GoogleCalendarRecurrenceJob job
    ) {
        return transactionTemplate.execute(
                status -> mappingQueryService
                        .listRecurrenceEventMappingsForJob(job.getIntegrationId(), job.getRecurrenceEventId()).stream()
                        .map(RecurrenceMappingSnapshot::from)
                        .toList());
    }

    private List<OverrideMappingScope> loadOverrideMappingScopes(GoogleCalendarRecurrenceJob job) {
        return transactionTemplate.execute(
                status -> mappingQueryService
                        .listRecurrenceEventMappingsForJob(job.getIntegrationId(), job.getRecurrenceEventId()).stream()
                        .map(master -> OverrideMappingScope.from(
                                master,
                                mappingQueryService.getOverrideMappingIfExists(master.getId(), job.getOriginStartAt())
                                        .orElse(null)
                        )).toList());
    }

    private void completeRecurrenceCreate(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            List<MappingExecutionResult> mappingResults,
            Long targetConnectionId,
            GoogleCalendarEventResponse createdEvent
    ) {
        Map<Long, GoogleCalendarRecurrenceEventMapping> mappingsById = findRecurrenceMappingsById(job);
        MappingOutcome outcome = applyMappingResults(mappingResults, mappingsById);
        createRecurrenceMapping(job, targetConnectionId, createdEvent, mappingsById);
        if (completeConflictOrSkip(job, workerToken, outcome)) {
            return;
        }
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private void completeRecurrenceUpdate(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            List<MappingExecutionResult> mappingResults
    ) {
        MappingOutcome outcome = applyMappingResults(mappingResults, findRecurrenceMappingsById(job));
        if (completeConflictOrSkip(job, workerToken, outcome)) {
            return;
        }
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private void completeRecurrenceDelete(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            List<MappingExecutionResult> mappingResults
    ) {
        Map<Long, GoogleCalendarRecurrenceEventMapping> mappingsById = findRecurrenceMappingsById(job);
        MappingOutcome outcome = applyMappingResults(mappingResults, mappingsById);
        if (completeConflictOrSkip(job, workerToken, outcome)) {
            return;
        }
        mappingResults.stream()
                .filter(MappingExecutionResult::providerDeleted)
                .map(result -> mappingsById.get(result.mappingId()))
                .filter(Objects::nonNull)
                .forEach(mappingCommandService::deleteRecurrenceAggregateMappings);
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private void createRecurrenceMapping(
            GoogleCalendarRecurrenceJob job,
            Long targetConnectionId,
            GoogleCalendarEventResponse createdEvent,
            Map<Long, GoogleCalendarRecurrenceEventMapping> mappingsById
    ) {
        if (createdEvent == null || targetConnectionId == null) {
            return;
        }
        if (mappingsById.values().stream()
                .anyMatch(mapping -> mapping.getConnection().getId().equals(targetConnectionId))) {
            return;
        }
        connectionQueryService.listConnections(job.getIntegrationId()).stream()
                .filter(connection -> connection.getId().equals(targetConnectionId))
                .findFirst()
                .ifPresent(connection -> mappingCommandService.createRecurrenceEventMapping(
                        new GoogleCalendarRecurrenceEventMapping(
                                connection,
                                job.getRecurrenceEventId(),
                                createdEvent.id(),
                                createdEvent.etag()
                        )
                ));
    }

    private void completeOverride(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            List<OverrideExecutionResult> results
    ) {
        Map<Long, GoogleCalendarRecurrenceEventMapping> masters = findRecurrenceMappingsById(job);
        MappingOutcome outcome = MappingOutcome.APPLIED;
        for (OverrideExecutionResult result : results) {
            GoogleCalendarRecurrenceEventMapping master = masters.get(result.masterId());
            if (master == null) {
                continue;
            }
            if (result.masterConflict()) {
                master.markConflicted();
                outcome = outcome.merge(MappingOutcome.CONFLICT_DETECTED);
                continue;
            }
            GoogleCalendarRecurrenceOverrideMapping override = result.overrideId() == null ? null
                    : mappingQueryService.getOverrideMappingIfExists(master.getId(), job.getOriginStartAt())
                            .orElse(null);
            if (result.alreadyConflicted()) {
                outcome = outcome.merge(MappingOutcome.ALREADY_CONFLICTED);
            }
            if (result.overrideConflict()) {
                if (override == null && result.externalId() != null) {
                    override = mappingCommandService.createOverrideMapping(
                            new GoogleCalendarRecurrenceOverrideMapping(
                                    master,
                                    job.getOriginStartAt(), result.externalId(), result.expectedEtag()
                            ));
                }
                if (override != null) {
                    override.markConflicted();
                } else {
                    master.markConflicted();
                }
                outcome = outcome.merge(MappingOutcome.CONFLICT_DETECTED);
            } else if (override != null && result.localChanged()) {
                override.markLocalChanged();
            } else if (override != null && result.deleted()) {
                mappingCommandService.deleteOverrideMappings(List.of(override));
            } else if (result.updatedEtag() != null) {
                if (override == null) {
                    mappingCommandService.createOverrideMapping(new GoogleCalendarRecurrenceOverrideMapping(
                            master, job.getOriginStartAt(), result.externalId(), result.updatedEtag()));
                } else if (override.getProviderEtag().equals(result.expectedEtag())) {
                    override.updateProviderEtag(result.updatedEtag());
                } else {
                    override.markConflicted();
                    outcome = outcome.merge(MappingOutcome.CONFLICT_DETECTED);
                }
            }
        }
        if (completeConflictOrSkip(job, workerToken, outcome)) {
            return;
        }
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private Map<Long, GoogleCalendarRecurrenceEventMapping> findRecurrenceMappingsById(
            GoogleCalendarRecurrenceJob job
    ) {
        Map<Long, GoogleCalendarRecurrenceEventMapping> result = new HashMap<>();
        mappingQueryService.listRecurrenceEventMappingsForJob(
                job.getIntegrationId(), job.getRecurrenceEventId()).forEach(mapping -> result.put(
                mapping.getId(),
                mapping
        ));
        return result;
    }

    private MappingOutcome applyMappingResults(
            List<MappingExecutionResult> results,
            Map<Long, GoogleCalendarRecurrenceEventMapping> current
    ) {
        MappingOutcome outcome = MappingOutcome.APPLIED;
        for (MappingExecutionResult result : results) {
            GoogleCalendarRecurrenceEventMapping mapping = current.get(result.mappingId());
            if (mapping == null) {
                continue;
            }
            if (mapping.isConflicted() && result.outcome() == MappingOutcome.APPLIED) {
                outcome = outcome.merge(MappingOutcome.ALREADY_CONFLICTED);
                continue;
            }
            if (result.outcome() == MappingOutcome.CONFLICT_DETECTED) {
                mapping.markConflicted();
                outcome = outcome.merge(MappingOutcome.CONFLICT_DETECTED);
                continue;
            }
            if (result.localChangeDetected()) {
                mapping.markLocalChanged();
            }
            if (result.updatedProviderEtag() != null) {
                if (mapping.getProviderEtag().equals(result.expectedProviderEtag())) {
                    mapping.updateProviderEtag(result.updatedProviderEtag());
                } else {
                    mapping.markConflicted();
                    outcome = outcome.merge(MappingOutcome.CONFLICT_DETECTED);
                }
            }
            outcome = outcome.merge(result.outcome());
        }
        return outcome;
    }

    private MappingOutcome mappingOutcome(List<MappingExecutionResult> mappingResults) {
        return mappingResults.stream()
                .map(MappingExecutionResult::outcome)
                .reduce(MappingOutcome.APPLIED, MappingOutcome::merge);
    }

    private boolean completeConflictOrSkip(
            GoogleCalendarRecurrenceJob job,
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

    private GoogleRecurrenceJobPayload readRecurrenceSnapshot(GoogleCalendarRecurrenceJob job) {
        try {
            GoogleRecurrenceJobPayload payload = objectMapper.readValue(
                    job.getTargetPayload(),
                    GoogleRecurrenceJobPayload.class
            );
            if (payload == null) {
                throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
            }
            return payload;
        } catch (JacksonException exception) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID, exception);
        }
    }

    private GoogleRecurrenceOverrideJobPayload readRecurrenceOverrideSnapshot(GoogleCalendarRecurrenceJob job) {
        try {
            GoogleRecurrenceOverrideJobPayload payload = objectMapper.readValue(
                    job.getTargetPayload(),
                    GoogleRecurrenceOverrideJobPayload.class
            );
            if (payload == null) {
                throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
            }
            return payload;
        } catch (JacksonException exception) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID, exception);
        }
    }

    private record RecurrenceMappingSnapshot(
            Long mappingId,
            Long connectionId,
            GoogleCalendarConnectionState state,
            String externalId,
            String etag,
            boolean conflicted
    ) {
        static RecurrenceMappingSnapshot from(GoogleCalendarRecurrenceEventMapping mapping) {
            return new RecurrenceMappingSnapshot(
                    mapping.getId(), mapping.getConnection().getId(),
                    mapping.getConnection().getState(), mapping.getExternalEventId(),
                    mapping.getProviderEtag(), mapping.isConflicted()
            );
        }
    }

    private record OverrideMappingScope(
            RecurrenceMappingSnapshot master,
            Long overrideId,
            String externalId,
            String etag,
            boolean overrideConflicted
    ) {
        static OverrideMappingScope from(
                GoogleCalendarRecurrenceEventMapping master,
                GoogleCalendarRecurrenceOverrideMapping override
        ) {
            return new OverrideMappingScope(
                    RecurrenceMappingSnapshot.from(master), override == null ? null : override.getId(),
                    override == null ? null : override.getExternalEventId(),
                    override == null ? null : override.getProviderEtag(),
                    override != null && override.isConflicted()
            );
        }
    }

    private record MappingExecutionResult(
            Long mappingId,
            MappingOutcome outcome,
            boolean localChangeDetected,
            String expectedProviderEtag,
            String updatedProviderEtag,
            boolean providerDeleted
    ) {
        static MappingExecutionResult updated(Long id, String expected, String updated) {
            return new MappingExecutionResult(
                    id, MappingOutcome.APPLIED, false, expected, updated, false);
        }

        static MappingExecutionResult conflictDetected(Long id) {
            return new MappingExecutionResult(
                    id, MappingOutcome.CONFLICT_DETECTED, false, null, null, false);
        }

        static MappingExecutionResult alreadyConflicted(Long id) {
            return new MappingExecutionResult(
                    id, MappingOutcome.ALREADY_CONFLICTED, false, null, null, false);
        }

        static MappingExecutionResult localChanged(Long id) {
            return new MappingExecutionResult(
                    id, MappingOutcome.APPLIED, true, null, null, false);
        }

        static MappingExecutionResult deleted(Long id) {
            return new MappingExecutionResult(
                    id, MappingOutcome.APPLIED, false, null, null, true);
        }
    }

    private record OverrideExecutionResult(Long masterId, Long overrideId, boolean masterConflict,
                                           boolean overrideConflict, boolean alreadyConflicted,
                                           boolean localChanged, boolean deleted, String externalId,
                                           String expectedEtag, String updatedEtag) {
        static OverrideExecutionResult applied(Long master, Long override) {
            return new OverrideExecutionResult(
                    master, override, false, false, false, false, false, null, null, null);
        }

        static OverrideExecutionResult updated(
                Long master, Long override, String external, String expected, String updated
        ) {
            return new OverrideExecutionResult(
                    master, override, false, false, false, false, false, external, expected, updated);
        }

        static OverrideExecutionResult deleted(Long master, Long override) {
            return new OverrideExecutionResult(
                    master, override, false, false, false, false, true, null, null, null);
        }

        static OverrideExecutionResult localChanged(Long master, Long override) {
            return new OverrideExecutionResult(
                    master, override, false, false, false, true, false, null, null, null);
        }

        static OverrideExecutionResult masterConflict(Long master, Long override) {
            return new OverrideExecutionResult(
                    master, override, true, false, false, false, false, null, null, null);
        }

        static OverrideExecutionResult overrideConflict(Long master, Long override) {
            return overrideConflict(master, override, null, null);
        }

        static OverrideExecutionResult overrideConflict(
                Long master, Long override, String external, String expected
        ) {
            return new OverrideExecutionResult(
                    master, override, false, true, false, false, false, external, expected, null);
        }

        static OverrideExecutionResult alreadyConflicted(Long master, Long override) {
            return new OverrideExecutionResult(
                    master, override, false, false, true, false, false, null, null, null);
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
