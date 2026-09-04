package com.calio.calendar.integration.sync.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

@Entity
@DiscriminatorValue("SYNC")
public class GoogleCalendarSyncJob extends GoogleOperationJob {

    @Enumerated(EnumType.STRING)
    @Column(name = "job_trigger", nullable = false, updatable = false, length = 32)
    private GoogleOperationJobTrigger trigger;

    protected GoogleCalendarSyncJob() {
    }

    public static GoogleCalendarSyncJob create(String operationId, Long integrationId, Long accountId,
                                                long integrationSequence, GoogleOperationJobTrigger trigger,
                                                Instant runnableAt) {
        if (trigger != GoogleOperationJobTrigger.MANUAL && trigger != GoogleOperationJobTrigger.PERIODIC) {
            throw new IllegalArgumentException("Sync Google operation trigger must be MANUAL or PERIODIC");
        }
        GoogleCalendarSyncJob job = new GoogleCalendarSyncJob();
        job.initialize(operationId, integrationId, accountId, integrationSequence,
                GoogleOperationJobKind.SYNC, SYNC_SCOPE, SYNC_KEY, runnableAt);
        job.trigger = trigger;
        return job;
    }

    public GoogleOperationJobTrigger getTrigger() { return trigger; }
}
