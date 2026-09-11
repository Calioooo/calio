package com.calio.calendar.integration.sync;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventVersionConflictException;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventWriteRequest;
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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class GoogleCalendarRecurrenceJobHandler {
    private final GoogleCalendarConnectionQueryService connectionQueryService;
    private final GoogleCalendarRecurrenceMappingQueryService mappingQueryService;
    private final GoogleCalendarRecurrenceMappingCommandService mappingCommandService;
    private final GoogleCalendarAccessTokenService accessTokenService;
    private final GoogleCalendarEventsClient eventsClient;
    private final ObjectMapper objectMapper;
    private final GoogleOperationJobService jobService;
    private final TransactionTemplate transactionTemplate;

    public GoogleCalendarRecurrenceJobHandler(
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
        Long connectionId = connectionQueryService
                .getConnectedConnectionByIntegrationIdIfExists(job.getIntegrationId())
                .map(connection -> connection.getId())
                .orElse(null);
        if (connectionId == null) {
            completeRecurrenceEventLocally(job, workerToken);
            return;
        }
        RecurrenceMappingSnapshot existingMapping = mappingQueryService
                .getRecurrenceEventMappingIfExists(connectionId, job.getRecurrenceEventId())
                .map(mapping -> RecurrenceMappingSnapshot.from(mapping, connectionId))
                .orElse(null);
        if (existingMapping == null) {
            GoogleCalendarEventResponse created = eventsClient.post(
                    accessTokenService.getAccessToken(connectionId),
                    GoogleCalendarEventWriteRequest.forRecurrenceCreate(payload, job.getProviderIdentity())
            );
            completeCreatedRecurrence(job, workerToken, connectionId, created);
            return;
        }
        if (existingMapping.conflicted()) {
            skipConflictedRecurrenceEvent(job, workerToken);
            return;
        }
        completeRecurrenceEventLocally(job, workerToken);
    }

    private void updateRecurrence(GoogleCalendarRecurrenceJob job, String workerToken) {
        GoogleRecurrenceJobPayload payload = readRecurrenceSnapshot(job);
        Long connectionId = connectionQueryService
                .getConnectedConnectionByIntegrationIdIfExists(job.getIntegrationId())
                .map(connection -> connection.getId())
                .orElse(null);
        if (connectionId == null) {
            completeRecurrenceEventLocally(job, workerToken);
            return;
        }
        RecurrenceMappingSnapshot mapping = mappingQueryService
                .getRecurrenceEventMappingIfExists(connectionId, job.getRecurrenceEventId())
                .map(found -> RecurrenceMappingSnapshot.from(found, connectionId))
                .orElse(null);
        if (mapping == null) {
            completeRecurrenceEventLocally(job, workerToken);
            return;
        }
        if (mapping.conflicted()) {
            skipConflictedRecurrenceEvent(job, workerToken);
            return;
        }
        patchRecurrence(job, workerToken, payload, connectionId, mapping);
    }

    private void deleteRecurrence(GoogleCalendarRecurrenceJob job, String workerToken) {
        Long connectionId = connectionQueryService
                .getConnectedConnectionByIntegrationIdIfExists(job.getIntegrationId())
                .map(connection -> connection.getId())
                .orElse(null);
        if (connectionId == null) {
            completeRecurrenceEventLocally(job, workerToken);
            return;
        }
        RecurrenceMappingSnapshot mapping = mappingQueryService
                .getRecurrenceEventMappingIfExists(connectionId, job.getRecurrenceEventId())
                .map(found -> RecurrenceMappingSnapshot.from(found, connectionId))
                .orElse(null);
        if (mapping == null) {
            completeRecurrenceEventLocally(job, workerToken);
            return;
        }
        if (mapping.conflicted()) {
            skipConflictedRecurrenceEvent(job, workerToken);
            return;
        }
        try {
            eventsClient.delete(
                    accessTokenService.getAccessToken(connectionId),
                    mapping.externalId(), mapping.etag()
            );
            transactionTemplate.executeWithoutResult(status ->
                    completeRecurrenceDelete(job, workerToken, mapping.mappingId()));
        } catch (GoogleCalendarEventVersionConflictException exception) {
            completeRecurrenceEventConflict(job, workerToken, mapping.mappingId());
        }
    }

    private void patchRecurrence(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            GoogleRecurrenceJobPayload payload,
            Long connectionId,
            RecurrenceMappingSnapshot mapping
    ) {
        String token = accessTokenService.getAccessToken(connectionId);
        GoogleCalendarEventResponse current = eventsClient.getEvent(token, mapping.externalId()).orElse(null);
        if (current == null || !mapping.etag().equals(current.etag())) {
            completeRecurrenceEventConflict(job, workerToken, mapping.mappingId());
            return;
        }
        try {
            GoogleCalendarEventResponse updated = eventsClient.patch(
                    token, mapping.externalId(), mapping.etag(),
                    GoogleCalendarEventWriteRequest.forRecurrenceUpdate(payload)
            );
            transactionTemplate.executeWithoutResult(status -> completeRecurrenceUpdate(
                    job, workerToken, mapping.mappingId(), mapping.etag(), updated.etag()));
        } catch (GoogleCalendarEventVersionConflictException exception) {
            completeRecurrenceEventConflict(job, workerToken, mapping.mappingId());
        }
    }

    private void upsertOverride(GoogleCalendarRecurrenceJob job, String workerToken) {
        GoogleRecurrenceOverrideJobPayload payload = readRecurrenceOverrideSnapshot(job);
        OverrideMappingScope scope = loadConnectedOverrideScope(job);
        if (scope == null) {
            completeOverrideWithoutProviderWrite(job, workerToken, false);
            return;
        }
        if (scope.recurrenceEventMapping().conflicted() || scope.overrideConflicted()) {
            completeOverrideWithoutProviderWrite(job, workerToken, true);
            return;
        }
        applyOverrideUpdate(job, workerToken, scope, payload);
    }

    private void deleteOverride(GoogleCalendarRecurrenceJob job, String workerToken) {
        OverrideMappingScope scope = loadConnectedOverrideScope(job);
        if (scope == null) {
            completeOverrideWithoutProviderWrite(job, workerToken, false);
            return;
        }
        if (scope.recurrenceEventMapping().conflicted() || scope.overrideConflicted()) {
            completeOverrideWithoutProviderWrite(job, workerToken, true);
            return;
        }
        applyOverrideDelete(job, workerToken, scope);
    }

    private void applyOverrideUpdate(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            OverrideMappingScope scope,
            GoogleRecurrenceOverrideJobPayload payload
    ) {
        RecurrenceMappingSnapshot recurrenceEventMapping = scope.recurrenceEventMapping();
        String token = accessTokenService.getAccessToken(recurrenceEventMapping.connectionId());
        GoogleCalendarEventResponse providerRecurrenceEvent = eventsClient.getEvent(
                token, recurrenceEventMapping.externalId()).orElse(null);
        if (providerRecurrenceEvent == null || !recurrenceEventMapping.etag().equals(providerRecurrenceEvent.etag())) {
            completeRecurrenceEventConflictForOverride(job, workerToken, recurrenceEventMapping.mappingId());
            return;
        }
        GoogleCalendarEventResponse occurrence = getOverrideOccurrence(job, scope, token);
        if (isMissingUnmappedOccurrence(scope, occurrence)) {
            completeRecurrenceEventConflictForOverride(job, workerToken, recurrenceEventMapping.mappingId());
            return;
        }
        if (isChangedMappedOccurrence(scope, occurrence)) {
            completeOverrideConflict(job, workerToken, recurrenceEventMapping.mappingId(), null, null);
            return;
        }
        String expected = scope.overrideId() == null ? occurrence.etag() : scope.etag();
        try {
            GoogleCalendarEventResponse updated = eventsClient.patch(
                    token, occurrence.id(), expected,
                    GoogleCalendarEventWriteRequest.forOverrideUpdate(payload)
            );
            transactionTemplate.executeWithoutResult(status -> completeOverrideUpdate(
                    job, workerToken, recurrenceEventMapping.mappingId(), occurrence.id(), expected, updated.etag()));
        } catch (GoogleCalendarEventVersionConflictException exception) {
            completeOverrideConflict(
                    job, workerToken, recurrenceEventMapping.mappingId(), occurrence.id(), expected);
        }
    }

    private void applyOverrideDelete(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            OverrideMappingScope scope
    ) {
        RecurrenceMappingSnapshot recurrenceEventMapping = scope.recurrenceEventMapping();
        String token = accessTokenService.getAccessToken(recurrenceEventMapping.connectionId());
        GoogleCalendarEventResponse providerRecurrenceEvent = eventsClient.getEvent(
                token, recurrenceEventMapping.externalId()).orElse(null);
        if (providerRecurrenceEvent == null || !recurrenceEventMapping.etag().equals(providerRecurrenceEvent.etag())) {
            completeRecurrenceEventConflictForOverride(job, workerToken, recurrenceEventMapping.mappingId());
            return;
        }
        GoogleCalendarEventResponse occurrence = getOverrideOccurrence(job, scope, token);
        if (isMissingUnmappedOccurrence(scope, occurrence)) {
            completeRecurrenceEventConflictForOverride(job, workerToken, recurrenceEventMapping.mappingId());
            return;
        }
        if (isChangedMappedOccurrence(scope, occurrence)) {
            completeOverrideConflict(job, workerToken, recurrenceEventMapping.mappingId(), null, null);
            return;
        }
        String expected = scope.overrideId() == null ? occurrence.etag() : scope.etag();
        try {
            eventsClient.delete(token, occurrence.id(), expected);
            transactionTemplate.executeWithoutResult(status -> completeOverrideDelete(
                    job, workerToken, recurrenceEventMapping.mappingId()));
        } catch (GoogleCalendarEventVersionConflictException exception) {
            completeOverrideConflict(
                    job, workerToken, recurrenceEventMapping.mappingId(), occurrence.id(), expected);
        }
    }

    private GoogleCalendarEventResponse getOverrideOccurrence(
            GoogleCalendarRecurrenceJob job,
            OverrideMappingScope scope,
            String token
    ) {
        if (scope.overrideId() == null) {
            return eventsClient.getRecurrenceOccurrence(
                    token, scope.recurrenceEventMapping().externalId(), job.getOriginStartAt()).orElse(null);
        }
        return eventsClient.getEvent(token, scope.externalId()).orElse(null);
    }

    private boolean isMissingUnmappedOccurrence(
            OverrideMappingScope scope,
            GoogleCalendarEventResponse occurrence
    ) {
        return scope.overrideId() == null
                && (occurrence == null || occurrence.isCancelled());
    }

    private boolean isChangedMappedOccurrence(
            OverrideMappingScope scope,
            GoogleCalendarEventResponse occurrence
    ) {
        return scope.overrideId() != null
                && (occurrence == null || !scope.etag().equals(occurrence.etag()));
    }

    private OverrideMappingScope loadConnectedOverrideScope(GoogleCalendarRecurrenceJob job) {
        Long connectionId = connectionQueryService
                .getConnectedConnectionByIntegrationIdIfExists(job.getIntegrationId())
                .map(connection -> connection.getId())
                .orElse(null);
        if (connectionId == null) {
            return null;
        }
        RecurrenceMappingSnapshot recurrenceEvent = mappingQueryService
                .getRecurrenceEventMappingIfExists(connectionId, job.getRecurrenceEventId())
                .map(mapping -> RecurrenceMappingSnapshot.from(mapping, connectionId))
                .orElse(null);
        if (recurrenceEvent == null) {
            return null;
        }
        GoogleCalendarRecurrenceOverrideMapping override = mappingQueryService
                .getOverrideMappingIfExists(recurrenceEvent.mappingId(), job.getOriginStartAt())
                .orElse(null);
        return OverrideMappingScope.from(recurrenceEvent, override);
    }

    private void completeCreatedRecurrence(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            Long connectionId,
            GoogleCalendarEventResponse created
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            markInactiveRecurrenceEventMappingsLocalChanged(job);
            createRecurrenceMappingIfAbsent(job, connectionId, created);
            jobService.succeed(job.getId(), job.getAccountId(), workerToken);
        });
    }

    private void completeRecurrenceUpdate(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            Long mappingId,
            String expectedEtag,
            String updatedEtag
    ) {
        markInactiveRecurrenceEventMappingsLocalChanged(job);
        GoogleCalendarRecurrenceEventMapping mapping = mappingQueryService
                .getRecurrenceEventMappingIfExists(mappingId)
                .orElse(null);
        if (mapping == null) {
            jobService.succeed(job.getId(), job.getAccountId(), workerToken);
            return;
        }
        if (mapping.isConflicted()) {
            jobService.skipConflictedScope(job.getId(), job.getAccountId(), workerToken);
            return;
        }
        if (!mapping.getProviderEtag().equals(expectedEtag)) {
            mapping.markConflicted();
            jobService.completeWithConflict(job.getId(), job.getAccountId(), workerToken);
            return;
        }
        mapping.updateProviderEtag(updatedEtag);
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private void completeRecurrenceDelete(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            Long mappingId
    ) {
        markInactiveRecurrenceEventMappingsLocalChanged(job);
        mappingQueryService.getRecurrenceEventMappingIfExists(mappingId)
                .ifPresent(mappingCommandService::deleteRecurrenceAggregateMappings);
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private void createRecurrenceMappingIfAbsent(
            GoogleCalendarRecurrenceJob job,
            Long connectionId,
            GoogleCalendarEventResponse created
    ) {
        if (mappingQueryService.getRecurrenceEventMappingIfExists(
                connectionId, job.getRecurrenceEventId()).isPresent()) {
            return;
        }
        connectionQueryService.getConnectionIfExists(connectionId)
                .ifPresent(connection -> mappingCommandService.createRecurrenceEventMapping(
                        new GoogleCalendarRecurrenceEventMapping(
                                connection,
                                job.getRecurrenceEventId(),
                                created.id(),
                                created.etag()
                        )
                ));
    }

    private void completeRecurrenceEventLocally(
            GoogleCalendarRecurrenceJob job,
            String workerToken
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            markInactiveRecurrenceEventMappingsLocalChanged(job);
            jobService.succeed(job.getId(), job.getAccountId(), workerToken);
        });
    }

    private void skipConflictedRecurrenceEvent(
            GoogleCalendarRecurrenceJob job,
            String workerToken
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            markInactiveRecurrenceEventMappingsLocalChanged(job);
            jobService.skipConflictedScope(job.getId(), job.getAccountId(), workerToken);
        });
    }

    private void completeRecurrenceEventConflict(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            Long mappingId
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            markInactiveRecurrenceEventMappingsLocalChanged(job);
            GoogleCalendarRecurrenceEventMapping mapping = mappingQueryService
                    .getRecurrenceEventMappingIfExists(mappingId)
                    .orElse(null);
            if (mapping == null) {
                jobService.succeed(job.getId(), job.getAccountId(), workerToken);
                return;
            }
            mapping.markConflicted();
            jobService.completeWithConflict(job.getId(), job.getAccountId(), workerToken);
        });
    }

    private void completeRecurrenceEventConflictForOverride(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            Long recurrenceEventMappingId
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            markInactiveOverrideMappingsLocalChanged(job);
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = mappingQueryService
                    .getRecurrenceEventMappingIfExists(recurrenceEventMappingId)
                    .orElse(null);
            if (recurrenceEventMapping == null) {
                jobService.succeed(job.getId(), job.getAccountId(), workerToken);
                return;
            }
            recurrenceEventMapping.markConflicted();
            jobService.completeWithConflict(job.getId(), job.getAccountId(), workerToken);
        });
    }

    private void completeOverrideWithoutProviderWrite(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            boolean conflicted
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            markInactiveOverrideMappingsLocalChanged(job);
            if (conflicted) {
                jobService.skipConflictedScope(job.getId(), job.getAccountId(), workerToken);
                return;
            }
            jobService.succeed(job.getId(), job.getAccountId(), workerToken);
        });
    }

    private void completeOverrideUpdate(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            Long recurrenceEventMappingId,
            String externalId,
            String expectedEtag,
            String updatedEtag
    ) {
        markInactiveOverrideMappingsLocalChanged(job);
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = mappingQueryService
                .getRecurrenceEventMappingIfExists(recurrenceEventMappingId)
                .orElse(null);
        if (recurrenceEventMapping == null) {
            jobService.succeed(job.getId(), job.getAccountId(), workerToken);
            return;
        }
        GoogleCalendarRecurrenceOverrideMapping override = mappingQueryService
                .getOverrideMappingIfExists(recurrenceEventMappingId, job.getOriginStartAt())
                .orElse(null);
        if (override == null) {
            mappingCommandService.createOverrideMapping(new GoogleCalendarRecurrenceOverrideMapping(
                    recurrenceEventMapping, job.getOriginStartAt(), externalId, updatedEtag));
            jobService.succeed(job.getId(), job.getAccountId(), workerToken);
            return;
        }
        if (!override.getProviderEtag().equals(expectedEtag)) {
            override.markConflicted();
            jobService.completeWithConflict(job.getId(), job.getAccountId(), workerToken);
            return;
        }
        override.updateProviderEtag(updatedEtag);
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private void completeOverrideDelete(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            Long recurrenceEventMappingId
    ) {
        markInactiveOverrideMappingsLocalChanged(job);
        mappingQueryService.getOverrideMappingIfExists(recurrenceEventMappingId, job.getOriginStartAt())
                .ifPresent(override -> mappingCommandService.deleteOverrideMappings(List.of(override)));
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private void completeOverrideConflict(
            GoogleCalendarRecurrenceJob job,
            String workerToken,
            Long recurrenceEventMappingId,
            String externalId,
            String expectedEtag
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            markInactiveOverrideMappingsLocalChanged(job);
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = mappingQueryService
                    .getRecurrenceEventMappingIfExists(recurrenceEventMappingId)
                    .orElse(null);
            if (recurrenceEventMapping == null) {
                jobService.succeed(job.getId(), job.getAccountId(), workerToken);
                return;
            }
            GoogleCalendarRecurrenceOverrideMapping override = mappingQueryService
                    .getOverrideMappingIfExists(recurrenceEventMappingId, job.getOriginStartAt())
                    .orElse(null);
            if (override == null && externalId != null) {
                override = mappingCommandService.createOverrideMapping(
                        new GoogleCalendarRecurrenceOverrideMapping(
                                recurrenceEventMapping, job.getOriginStartAt(), externalId, expectedEtag));
            }
            if (override != null) {
                override.markConflicted();
            } else {
                recurrenceEventMapping.markConflicted();
            }
            jobService.completeWithConflict(job.getId(), job.getAccountId(), workerToken);
        });
    }

    private void markInactiveRecurrenceEventMappingsLocalChanged(GoogleCalendarRecurrenceJob job) {
        mappingCommandService.markInactiveRecurrenceEventMappingsLocalChanged(
                job.getIntegrationId(), job.getRecurrenceEventId());
    }

    private void markInactiveOverrideMappingsLocalChanged(GoogleCalendarRecurrenceJob job) {
        mappingCommandService.markInactiveOverrideMappingsLocalChanged(
                job.getIntegrationId(), job.getRecurrenceEventId(), job.getOriginStartAt());
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
            String externalId,
            String etag,
            boolean conflicted
    ) {
        static RecurrenceMappingSnapshot from(
                GoogleCalendarRecurrenceEventMapping mapping,
                Long connectionId
        ) {
            return new RecurrenceMappingSnapshot(
                    mapping.getId(), connectionId,
                    mapping.getExternalEventId(),
                    mapping.getProviderEtag(), mapping.isConflicted()
            );
        }
    }

    private record OverrideMappingScope(
            RecurrenceMappingSnapshot recurrenceEventMapping,
            Long overrideId,
            String externalId,
            String etag,
            boolean overrideConflicted
    ) {
        static OverrideMappingScope from(
                RecurrenceMappingSnapshot recurrenceEventMapping,
                GoogleCalendarRecurrenceOverrideMapping override
        ) {
            return new OverrideMappingScope(
                    recurrenceEventMapping, override == null ? null : override.getId(),
                    override == null ? null : override.getExternalEventId(),
                    override == null ? null : override.getProviderEtag(),
                    override != null && override.isConflicted()
            );
        }
    }
}
