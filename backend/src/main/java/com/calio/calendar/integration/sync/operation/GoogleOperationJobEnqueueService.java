package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionCommandService;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationCommandService;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarSyncJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJobKind;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobTrigger;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJobKind;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceMasterJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class GoogleOperationJobEnqueueService {

    private final GoogleCalendarConnectionCommandService connectionCommandService;
    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleOperationJobCommandService jobCommandService;
    private final GoogleOperationWorker worker;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public GoogleOperationJobEnqueueService(
            GoogleCalendarConnectionCommandService connectionCommandService,
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleOperationJobCommandService jobCommandService,
            GoogleOperationWorker worker,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.connectionCommandService = connectionCommandService;
        this.integrationCommandService = integrationCommandService;
        this.jobCommandService = jobCommandService;
        this.worker = worker;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueueManualSync(Long accountId) {
        enqueueSync(accountId, GoogleOperationJobTrigger.MANUAL);
    }

    @Transactional
    public void enqueuePeriodicSync(Long accountId) {
        enqueueSync(accountId, GoogleOperationJobTrigger.PERIODIC);
    }

    private void enqueueSync(Long accountId, GoogleOperationJobTrigger trigger) {
        connectionCommandService.lockConnectedConnection(accountId);
        GoogleCalendarIntegration integration = integrationCommandService.tryLockIntegration(accountId)
                .orElseThrow();
        GoogleCalendarSyncJob job = GoogleCalendarSyncJob.create(
                UUID.randomUUID().toString(),
                integration.getId(),
                accountId,
                integration.allocateGoogleOperationSequence(),
                trigger,
                Instant.now(clock)
        );
        jobCommandService.enqueueOperationJob(job);
        wakeAfterCommit(accountId);
    }

    @Transactional
    public boolean enqueueEventCreated(Long accountId, Event event) {
        return enqueueEventSnapshot(accountId, event, GoogleCalendarEventJobKind.CREATE);
    }

    @Transactional
    public boolean enqueueEventUpdated(Long accountId, Event event) {
        return enqueueEventSnapshot(accountId, event, GoogleCalendarEventJobKind.UPDATE);
    }

    @Transactional
    public boolean enqueueEventDeleted(Long accountId, Long eventId) {
        return enqueueEventJob(accountId, eventId, GoogleCalendarEventJobKind.DELETE, "{}");
    }

    private boolean enqueueEventSnapshot(Long accountId, Event event, GoogleCalendarEventJobKind kind) {
        return enqueueEventJob(
                accountId,
                event.getId(),
                kind,
                serializePayload(GoogleEventJobPayload.from(event))
        );
    }

    private boolean enqueueEventJob(
            Long accountId,
            Long eventId,
            GoogleCalendarEventJobKind kind,
            String targetPayload
    ) {
        var integration = integrationCommandService.tryLockIntegration(accountId).orElse(null);
        if (integration == null) {
            return false;
        }
        String operationId = UUID.randomUUID().toString();
        GoogleCalendarEventJob job = GoogleCalendarEventJob.create(
                operationId, integration.getId(), accountId,
                integration.allocateGoogleOperationSequence(), kind,
                eventId,
                providerIdentity(kind, operationId),
                targetPayload,
                Instant.now(clock)
        );
        jobCommandService.enqueueOperationJob(job);
        wakeAfterCommit(accountId);
        return true;
    }

    @Transactional
    public boolean enqueueRecurrenceMaster(
            Long accountId, Long recurrenceEventId, GoogleCalendarRecurrenceJobKind kind,
            GoogleRecurrenceMasterJobPayload payload
    ) {
        return enqueueRecurrenceJob(accountId, recurrenceEventId, kind, null,
                serializePayload(payload));
    }

    @Transactional
    public boolean enqueueRecurrenceMasterDeleted(Long accountId, Long recurrenceEventId) {
        return enqueueRecurrenceJob(accountId, recurrenceEventId,
                GoogleCalendarRecurrenceJobKind.MASTER_DELETE, null, "{}");
    }

    @Transactional
    public boolean enqueueRecurrenceOverride(
            Long accountId, Long recurrenceEventId, Instant originStartAt,
            GoogleRecurrenceOverrideJobPayload payload
    ) {
        return enqueueRecurrenceJob(accountId, recurrenceEventId,
                GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT, originStartAt,
                serializePayload(payload));
    }

    @Transactional
    public boolean enqueueRecurrenceOverrideDeleted(
            Long accountId, Long recurrenceEventId, Instant originStartAt
    ) {
        return enqueueRecurrenceJob(accountId, recurrenceEventId,
                GoogleCalendarRecurrenceJobKind.OVERRIDE_DELETE, originStartAt, "{}");
    }

    private boolean enqueueRecurrenceJob(
            Long accountId, Long recurrenceEventId, GoogleCalendarRecurrenceJobKind kind,
            Instant originStartAt, String targetPayload
    ) {
        var integration = integrationCommandService.tryLockIntegration(accountId).orElse(null);
        if (integration == null) {
            return false;
        }
        String operationId = UUID.randomUUID().toString();
        GoogleCalendarRecurrenceJob job = GoogleCalendarRecurrenceJob.create(
                operationId, integration.getId(), accountId,
                integration.allocateGoogleOperationSequence(), kind, recurrenceEventId,
                originStartAt, targetPayload,
                kind == GoogleCalendarRecurrenceJobKind.MASTER_CREATE
                        ? "c1" + operationId.replace("-", "") : null,
                Instant.now(clock));
        jobCommandService.enqueueOperationJob(job);
        wakeAfterCommit(accountId);
        return true;
    }

    private String providerIdentity(GoogleCalendarEventJobKind kind, String operationId) {
        if (kind != GoogleCalendarEventJobKind.CREATE) {
            return null;
        }
        return "c1" + operationId.replace("-", "");
    }

    private String serializePayload(GoogleEventJobPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Google Event job payload cannot be encoded", exception);
        }
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Google recurrence job payload cannot be encoded", exception);
        }
    }

    private void wakeAfterCommit(Long accountId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.wake(accountId);
            }
        });
    }
}
