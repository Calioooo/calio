package com.calio.calendar.integration.sync;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventVersionConflictException;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
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
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceMasterJobPayload;
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
            case MASTER_CREATE -> executeMasterUpsert(job, workerToken, true);
            case MASTER_UPDATE -> executeMasterUpsert(job, workerToken, false);
            case MASTER_DELETE -> executeMasterDelete(job, workerToken);
            case OVERRIDE_UPSERT -> executeOverride(job, workerToken, false);
            case OVERRIDE_DELETE -> executeOverride(job, workerToken, true);
        }
    }

    private void executeMasterUpsert(GoogleCalendarRecurrenceJob job, String workerToken, boolean create) {
        GoogleRecurrenceMasterJobPayload payload = read(job, GoogleRecurrenceMasterJobPayload.class);
        List<MasterSnapshot> mappings = loadMasters(job);
        List<Result> results = new ArrayList<>();
        mappings.forEach(mapping -> results.add(upsertMaster(mapping, payload)));
        Long target = null;
        GoogleCalendarEventResponse created = null;
        if (create && results.stream().noneMatch(result -> result.conflict() || result.alreadyConflicted())) {
            target = connectionQueryService.listConnections(job.getIntegrationId()).stream()
                    .filter(GoogleCalendarConnection::isConnected)
                    .filter(connection -> mappings.stream().noneMatch(mapping ->
                            mapping.connectionId().equals(connection.getId())))
                    .map(GoogleCalendarConnection::getId).findFirst().orElse(null);
            if (target != null) {
                created = eventsClient.insertRecurrenceEvent(
                        accessTokenService.getAccessToken(target), job.getProviderIdentity(), payload);
            }
        }
        Long creationTarget = target;
        GoogleCalendarEventResponse providerCreated = created;
        transactionTemplate.executeWithoutResult(status -> completeMasterUpsert(
                job, workerToken, results, creationTarget, providerCreated));
    }

    private Result upsertMaster(MasterSnapshot mapping, GoogleRecurrenceMasterJobPayload payload) {
        if (mapping.conflicted()) return Result.alreadyConflicted(mapping.mappingId());
        if (mapping.state() != GoogleCalendarConnectionState.CONNECTED) {
            return Result.localChanged(mapping.mappingId());
        }
        String token = accessTokenService.getAccessToken(mapping.connectionId());
        GoogleCalendarEventResponse current = eventsClient.getEvent(token, mapping.externalId()).orElse(null);
        if (current == null || !mapping.etag().equals(current.etag())) {
            return Result.conflicted(mapping.mappingId());
        }
        try {
            GoogleCalendarEventResponse updated = eventsClient.patchRecurrenceEvent(
                    token, mapping.externalId(), mapping.etag(), payload);
            return Result.updated(mapping.mappingId(), mapping.etag(), updated.etag());
        } catch (GoogleCalendarEventVersionConflictException exception) {
            return Result.conflicted(mapping.mappingId());
        }
    }

    private void executeMasterDelete(GoogleCalendarRecurrenceJob job, String workerToken) {
        List<Result> results = loadMasters(job).stream().map(this::deleteMaster).toList();
        transactionTemplate.executeWithoutResult(status -> completeMasterDelete(job, workerToken, results));
    }

    private Result deleteMaster(MasterSnapshot mapping) {
        if (mapping.conflicted()) return Result.alreadyConflicted(mapping.mappingId());
        if (mapping.state() != GoogleCalendarConnectionState.CONNECTED) {
            return Result.localChanged(mapping.mappingId());
        }
        try {
            eventsClient.deleteEvent(accessTokenService.getAccessToken(mapping.connectionId()),
                    mapping.externalId(), mapping.etag());
            return Result.deleted(mapping.mappingId());
        } catch (GoogleCalendarEventVersionConflictException exception) {
            return Result.conflicted(mapping.mappingId());
        }
    }

    private void executeOverride(GoogleCalendarRecurrenceJob job, String workerToken, boolean delete) {
        GoogleRecurrenceOverrideJobPayload payload = delete ? null
                : read(job, GoogleRecurrenceOverrideJobPayload.class);
        List<OverrideResult> results = loadOverrideScopes(job).stream()
                .map(scope -> applyOverride(job, scope, payload, delete)).toList();
        transactionTemplate.executeWithoutResult(status -> completeOverride(job, workerToken, results));
    }

    private OverrideResult applyOverride(
            GoogleCalendarRecurrenceJob job, OverrideScope scope,
            GoogleRecurrenceOverrideJobPayload payload, boolean delete
    ) {
        MasterSnapshot master = scope.master();
        if (master.conflicted() || scope.overrideConflicted()) {
            return OverrideResult.alreadyConflicted(master.mappingId(), scope.overrideId());
        }
        if (master.state() != GoogleCalendarConnectionState.CONNECTED) {
            return scope.overrideId() == null ? OverrideResult.applied(master.mappingId(), null)
                    : OverrideResult.localChanged(master.mappingId(), scope.overrideId());
        }
        String token = accessTokenService.getAccessToken(master.connectionId());
        GoogleCalendarEventResponse providerMaster = eventsClient.getEvent(
                token, master.externalId()).orElse(null);
        if (providerMaster == null || !master.etag().equals(providerMaster.etag())) {
            return OverrideResult.masterConflict(master.mappingId(), scope.overrideId());
        }
        GoogleCalendarEventResponse instance;
        if (scope.overrideId() == null) {
            instance = eventsClient.resolveRecurrenceInstance(
                    token, master.externalId(), job.getOriginStartAt()).orElse(null);
            if (instance == null) return OverrideResult.masterConflict(master.mappingId(), null);
            if (instance.isCancelled()) {
                return OverrideResult.masterConflict(master.mappingId(), null);
            }
        } else {
            instance = eventsClient.getEvent(token, scope.externalId()).orElse(null);
            if (instance == null || !scope.etag().equals(instance.etag())) {
                return OverrideResult.overrideConflict(master.mappingId(), scope.overrideId());
            }
        }
        String expected = scope.overrideId() == null ? instance.etag() : scope.etag();
        try {
            if (delete) {
                eventsClient.cancelRecurrenceInstance(token, instance.id(), expected);
                return OverrideResult.deleted(master.mappingId(), scope.overrideId());
            }
            GoogleCalendarEventResponse updated = eventsClient.patchRecurrenceInstance(
                    token, instance.id(), expected, payload);
            return OverrideResult.updated(master.mappingId(), scope.overrideId(),
                    instance.id(), expected, updated.etag());
        } catch (GoogleCalendarEventVersionConflictException exception) {
            return OverrideResult.overrideConflict(
                    master.mappingId(), scope.overrideId(), instance.id(), expected);
        }
    }

    private List<MasterSnapshot> loadMasters(GoogleCalendarRecurrenceJob job) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> mappingQueryService
                .listRecurrenceEventMappingsForJob(job.getIntegrationId(), job.getRecurrenceEventId())
                .stream().map(MasterSnapshot::from).toList()));
    }

    private List<OverrideScope> loadOverrideScopes(GoogleCalendarRecurrenceJob job) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> mappingQueryService
                .listRecurrenceEventMappingsForJob(job.getIntegrationId(), job.getRecurrenceEventId())
                .stream().map(master -> OverrideScope.from(master,
                        mappingQueryService.getOverrideMappingIfExists(master.getId(), job.getOriginStartAt())
                                .orElse(null))).toList()));
    }

    private void completeMasterUpsert(
            GoogleCalendarRecurrenceJob job, String workerToken, List<Result> results,
            Long creationTarget, GoogleCalendarEventResponse created
    ) {
        Map<Long, GoogleCalendarRecurrenceEventMapping> current = currentMasters(job);
        Outcome outcome = applyMasterResults(results, current);
        if (created != null && creationTarget != null && current.values().stream().noneMatch(mapping ->
                mapping.getConnection().getId().equals(creationTarget))) {
            connectionQueryService.listConnections(job.getIntegrationId()).stream()
                    .filter(connection -> connection.getId().equals(creationTarget)).findFirst()
                    .ifPresent(connection -> mappingCommandService.createRecurrenceEventMapping(
                            new GoogleCalendarRecurrenceEventMapping(connection,
                                    job.getRecurrenceEventId(), created.id(), created.etag())));
        }
        if (finishConflict(job, workerToken, outcome)) return;
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private void completeMasterDelete(
            GoogleCalendarRecurrenceJob job, String workerToken, List<Result> results
    ) {
        Map<Long, GoogleCalendarRecurrenceEventMapping> current = currentMasters(job);
        Outcome outcome = applyMasterResults(results, current);
        if (finishConflict(job, workerToken, outcome)) return;
        results.stream().filter(Result::providerDeleted).map(result -> current.get(result.mappingId()))
                .filter(Objects::nonNull).forEach(mappingCommandService::deleteRecurrenceAggregateMappings);
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private void completeOverride(
            GoogleCalendarRecurrenceJob job, String workerToken, List<OverrideResult> results
    ) {
        Map<Long, GoogleCalendarRecurrenceEventMapping> masters = currentMasters(job);
        Outcome outcome = Outcome.APPLIED;
        for (OverrideResult result : results) {
            GoogleCalendarRecurrenceEventMapping master = masters.get(result.masterId());
            if (master == null) continue;
            if (result.masterConflict()) {
                master.markConflicted(); outcome = outcome.merge(Outcome.CONFLICT); continue;
            }
            GoogleCalendarRecurrenceOverrideMapping override = result.overrideId() == null ? null
                    : mappingQueryService.getOverrideMappingIfExists(master.getId(), job.getOriginStartAt())
                            .orElse(null);
            if (result.alreadyConflicted()) outcome = outcome.merge(Outcome.ALREADY_CONFLICTED);
            if (result.overrideConflict()) {
                if (override == null && result.externalId() != null) {
                    override = mappingCommandService.createOverrideMapping(
                            new GoogleCalendarRecurrenceOverrideMapping(master,
                                    job.getOriginStartAt(), result.externalId(), result.expectedEtag()));
                }
                if (override != null) override.markConflicted(); else master.markConflicted();
                outcome = outcome.merge(Outcome.CONFLICT);
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
                    override.markConflicted(); outcome = outcome.merge(Outcome.CONFLICT);
                }
            }
        }
        if (finishConflict(job, workerToken, outcome)) return;
        jobService.succeed(job.getId(), job.getAccountId(), workerToken);
    }

    private Map<Long, GoogleCalendarRecurrenceEventMapping> currentMasters(GoogleCalendarRecurrenceJob job) {
        Map<Long, GoogleCalendarRecurrenceEventMapping> result = new HashMap<>();
        mappingQueryService.listRecurrenceEventMappingsForJob(
                job.getIntegrationId(), job.getRecurrenceEventId()).forEach(mapping -> result.put(mapping.getId(), mapping));
        return result;
    }

    private Outcome applyMasterResults(
            List<Result> results, Map<Long, GoogleCalendarRecurrenceEventMapping> current
    ) {
        Outcome outcome = Outcome.APPLIED;
        for (Result result : results) {
            GoogleCalendarRecurrenceEventMapping mapping = current.get(result.mappingId());
            if (mapping == null) continue;
            if (result.alreadyConflicted()) outcome = outcome.merge(Outcome.ALREADY_CONFLICTED);
            else if (result.conflict()) {
                mapping.markConflicted(); outcome = outcome.merge(Outcome.CONFLICT);
            }
            else if (result.localChanged()) mapping.markLocalChanged();
            else if (result.updatedEtag() != null) {
                if (mapping.getProviderEtag().equals(result.expectedEtag())) {
                    mapping.updateProviderEtag(result.updatedEtag());
                } else {
                    mapping.markConflicted(); outcome = outcome.merge(Outcome.CONFLICT);
                }
            }
        }
        return outcome;
    }

    private boolean finishConflict(GoogleCalendarRecurrenceJob job, String workerToken, Outcome outcome) {
        if (outcome == Outcome.ALREADY_CONFLICTED) {
            jobService.skipConflictedScope(job.getId(), job.getAccountId(), workerToken); return true;
        }
        if (outcome == Outcome.CONFLICT) {
            jobService.recordSyncConflict(job.getId(), job.getAccountId(), workerToken);
            jobService.completeSyncRun(job.getId(), job.getAccountId(), workerToken); return true;
        }
        return false;
    }

    private <T> T read(GoogleCalendarRecurrenceJob job, Class<T> type) {
        try {
            T payload = objectMapper.readValue(job.getTargetPayload(), type);
            if (payload == null) {
                throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID);
            }
            return payload;
        }
        catch (JacksonException exception) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID, exception);
        }
    }

    private record MasterSnapshot(Long mappingId, Long connectionId,
                                  GoogleCalendarConnectionState state, String externalId,
                                  String etag, boolean conflicted) {
        static MasterSnapshot from(GoogleCalendarRecurrenceEventMapping mapping) {
            return new MasterSnapshot(mapping.getId(), mapping.getConnection().getId(),
                    mapping.getConnection().getState(), mapping.getExternalEventId(),
                    mapping.getProviderEtag(), mapping.isConflicted());
        }
    }

    private record OverrideScope(MasterSnapshot master, Long overrideId, String externalId,
                                 String etag, boolean overrideConflicted) {
        static OverrideScope from(GoogleCalendarRecurrenceEventMapping master,
                                  GoogleCalendarRecurrenceOverrideMapping override) {
            return new OverrideScope(MasterSnapshot.from(master), override == null ? null : override.getId(),
                    override == null ? null : override.getExternalEventId(),
                    override == null ? null : override.getProviderEtag(),
                    override != null && override.isConflicted());
        }
    }

    private record Result(Long mappingId, boolean conflict, boolean alreadyConflicted,
                          boolean localChanged, String expectedEtag, String updatedEtag,
                          boolean providerDeleted) {
        static Result updated(Long id, String expected, String updated) { return new Result(id,false,false,false,expected,updated,false); }
        static Result conflicted(Long id) { return new Result(id,true,false,false,null,null,false); }
        static Result alreadyConflicted(Long id) { return new Result(id,false,true,false,null,null,false); }
        static Result localChanged(Long id) { return new Result(id,false,false,true,null,null,false); }
        static Result deleted(Long id) { return new Result(id,false,false,false,null,null,true); }
    }

    private record OverrideResult(Long masterId, Long overrideId, boolean masterConflict,
                                  boolean overrideConflict, boolean alreadyConflicted,
                                  boolean localChanged, boolean deleted, String externalId,
                                  String expectedEtag, String updatedEtag) {
        static OverrideResult applied(Long master, Long override) { return new OverrideResult(master,override,false,false,false,false,false,null,null,null); }
        static OverrideResult updated(Long master, Long override, String external, String expected, String updated) { return new OverrideResult(master,override,false,false,false,false,false,external,expected,updated); }
        static OverrideResult deleted(Long master, Long override) { return new OverrideResult(master,override,false,false,false,false,true,null,null,null); }
        static OverrideResult localChanged(Long master, Long override) { return new OverrideResult(master,override,false,false,false,true,false,null,null,null); }
        static OverrideResult masterConflict(Long master, Long override) { return new OverrideResult(master,override,true,false,false,false,false,null,null,null); }
        static OverrideResult overrideConflict(Long master, Long override) { return overrideConflict(master, override, null, null); }
        static OverrideResult overrideConflict(Long master, Long override, String external, String expected) { return new OverrideResult(master,override,false,true,false,false,false,external,expected,null); }
        static OverrideResult alreadyConflicted(Long master, Long override) { return new OverrideResult(master,override,false,false,true,false,false,null,null,null); }
    }

    private enum Outcome {
        APPLIED, CONFLICT, ALREADY_CONFLICTED;

        private Outcome merge(Outcome other) {
            if (this == ALREADY_CONFLICTED || other == ALREADY_CONFLICTED) {
                return ALREADY_CONFLICTED;
            }
            if (this == CONFLICT || other == CONFLICT) {
                return CONFLICT;
            }
            return APPLIED;
        }
    }
}
