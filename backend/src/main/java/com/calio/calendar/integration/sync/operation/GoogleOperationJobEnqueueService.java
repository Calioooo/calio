package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionCommandService;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationCommandService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJobKind;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJobKind;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarSyncJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobTrigger;
import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import com.calio.calendar.singleevent.domain.SingleEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GoogleOperationJobEnqueueService {

  public static final class OutboundOperation {

    private final Long accountId;
    private final GoogleCalendarIntegration integration;

    private OutboundOperation(Long accountId, GoogleCalendarIntegration integration) {
      this.accountId = accountId;
      this.integration = integration;
    }
  }

  private final GoogleCalendarConnectionCommandService connectionCommandService;
  private final GoogleCalendarIntegrationCommandService integrationCommandService;
  private final GoogleOperationJobCommandService jobCommandService;
  private final GoogleOperationWorker worker;
  private final Clock clock;

  public GoogleOperationJobEnqueueService(
      GoogleCalendarConnectionCommandService connectionCommandService,
      GoogleCalendarIntegrationCommandService integrationCommandService,
      GoogleOperationJobCommandService jobCommandService,
      GoogleOperationWorker worker,
      Clock clock) {
    this.connectionCommandService = connectionCommandService;
    this.integrationCommandService = integrationCommandService;
    this.jobCommandService = jobCommandService;
    this.worker = worker;
    this.clock = clock;
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
    GoogleCalendarIntegration integration =
        integrationCommandService.tryLockIntegration(accountId).orElseThrow();
    GoogleCalendarSyncJob job =
        GoogleCalendarSyncJob.create(
            UUID.randomUUID().toString(),
            integration.getId(),
            accountId,
            integration.allocateGoogleOperationSequence(),
            trigger,
            Instant.now(clock));
    jobCommandService.enqueueOperationJob(job);
    wakeAfterCommit(accountId);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public OutboundOperation prepareOutboundOperation(Long accountId) {
    return new OutboundOperation(
        accountId, integrationCommandService.tryLockIntegration(accountId).orElse(null));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean enqueueEventCreated(OutboundOperation operation, SingleEvent event) {
    return enqueueEventSnapshot(operation, event, GoogleCalendarEventJobKind.CREATE);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean enqueueEventUpdated(OutboundOperation operation, SingleEvent event) {
    return enqueueEventSnapshot(operation, event, GoogleCalendarEventJobKind.UPDATE);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean enqueueEventDeleted(OutboundOperation operation, Long eventId) {
    return enqueueEventJob(operation, eventId, GoogleCalendarEventJobKind.DELETE, null);
  }

  private boolean enqueueEventSnapshot(
      OutboundOperation operation, SingleEvent event, GoogleCalendarEventJobKind kind) {
    return enqueueEventJob(operation, event.getId(), kind, GoogleEventJobPayload.from(event));
  }

  private boolean enqueueEventJob(
      OutboundOperation operation,
      Long eventId,
      GoogleCalendarEventJobKind kind,
      GoogleEventJobPayload targetPayload) {
    GoogleCalendarIntegration integration = operation.integration;
    if (integration == null) {
      return false;
    }
    String operationId = UUID.randomUUID().toString();
    GoogleCalendarEventJob job =
        GoogleCalendarEventJob.create(
            operationId,
            integration.getId(),
            operation.accountId,
            integration.allocateGoogleOperationSequence(),
            kind,
            eventId,
            providerIdentity(kind, operationId),
            targetPayload,
            Instant.now(clock));
    jobCommandService.enqueueOperationJob(job);
    wakeAfterCommit(operation.accountId);
    return true;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean enqueueRecurrence(
      OutboundOperation operation,
      Long recurrenceEventId,
      GoogleCalendarRecurrenceJobKind kind,
      GoogleRecurrenceJobPayload payload) {
    return enqueueRecurrenceJob(operation, recurrenceEventId, kind, null, payload);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean enqueueRecurrenceDeleted(OutboundOperation operation, Long recurrenceEventId) {
    return enqueueRecurrenceJob(
        operation,
        recurrenceEventId,
        GoogleCalendarRecurrenceJobKind.RECURRENCE_DELETE,
        null,
        null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean enqueueRecurrenceOverride(
      OutboundOperation operation,
      Long recurrenceEventId,
      Instant originStartAt,
      GoogleRecurrenceOverrideJobPayload payload) {
    return enqueueRecurrenceJob(
        operation,
        recurrenceEventId,
        GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT,
        originStartAt,
        GoogleRecurrenceJobPayload.from(payload));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean enqueueRecurrenceOverrideDeleted(
      OutboundOperation operation, Long recurrenceEventId, Instant originStartAt) {
    return enqueueRecurrenceJob(
        operation,
        recurrenceEventId,
        GoogleCalendarRecurrenceJobKind.OVERRIDE_DELETE,
        originStartAt,
        null);
  }

  private boolean enqueueRecurrenceJob(
      OutboundOperation operation,
      Long recurrenceEventId,
      GoogleCalendarRecurrenceJobKind kind,
      Instant originStartAt,
      GoogleRecurrenceJobPayload targetPayload) {
    GoogleCalendarIntegration integration = operation.integration;
    if (integration == null) {
      return false;
    }
    String operationId = UUID.randomUUID().toString();
    GoogleCalendarRecurrenceJob job =
        GoogleCalendarRecurrenceJob.create(
            operationId,
            integration.getId(),
            operation.accountId,
            integration.allocateGoogleOperationSequence(),
            kind,
            recurrenceEventId,
            originStartAt,
            targetPayload,
            kind == GoogleCalendarRecurrenceJobKind.RECURRENCE_CREATE
                ? "c1" + operationId.replace("-", "")
                : null,
            Instant.now(clock));
    jobCommandService.enqueueOperationJob(job);
    wakeAfterCommit(operation.accountId);
    return true;
  }

  private String providerIdentity(GoogleCalendarEventJobKind kind, String operationId) {
    if (kind != GoogleCalendarEventJobKind.CREATE) {
      return null;
    }
    return "c1" + operationId.replace("-", "");
  }

  private void wakeAfterCommit(Long accountId) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            worker.wake(accountId);
          }
        });
  }
}
