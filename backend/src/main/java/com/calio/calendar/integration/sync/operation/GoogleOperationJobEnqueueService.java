package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionCommandService;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationCommandService;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarSyncJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobKind;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobTrigger;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
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
        return enqueueEventSnapshot(accountId, event, GoogleOperationJobKind.CREATE);
    }

    @Transactional
    public boolean enqueueEventUpdated(Long accountId, Event event) {
        return enqueueEventSnapshot(accountId, event, GoogleOperationJobKind.UPDATE);
    }

    @Transactional
    public boolean enqueueEventDeleted(Long accountId, Long eventId) {
        return enqueueEventJob(accountId, eventId, GoogleOperationJobKind.DELETE, "{}");
    }

    private boolean enqueueEventSnapshot(Long accountId, Event event, GoogleOperationJobKind kind) {
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
            GoogleOperationJobKind kind,
            String targetPayload
    ) {
        var integration = integrationCommandService.tryLockIntegration(accountId).orElse(null);
        if (integration == null) {
            return false;
        }
        GoogleCalendarEventJob job = GoogleCalendarEventJob.create(
                UUID.randomUUID().toString(), integration.getId(), accountId,
                integration.allocateGoogleOperationSequence(), kind,
                eventId,
                providerIdentity(kind, integration.getId(), eventId),
                targetPayload,
                Instant.now(clock)
        );
        jobCommandService.enqueueOperationJob(job);
        wakeAfterCommit(accountId);
        return true;
    }

    private String providerIdentity(GoogleOperationJobKind kind, Long integrationId, Long eventId) {
        if (kind != GoogleOperationJobKind.CREATE) {
            return null;
        }
        return "c1" + "%016x".formatted(integrationId) + "%016x".formatted(eventId);
    }

    private String serializePayload(GoogleEventJobPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Google Event job payload cannot be encoded", exception);
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
