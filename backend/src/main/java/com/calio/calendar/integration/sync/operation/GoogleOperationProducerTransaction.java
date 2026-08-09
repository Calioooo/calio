package com.calio.calendar.integration.service;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GoogleOperationProducerTransaction {

    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleOperationJobCommandService jobCommandService;
    private final GoogleOperationWorker worker;
    private final Clock clock;

    public GoogleOperationProducerTransaction(
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleOperationJobCommandService jobCommandService,
            GoogleOperationWorker worker,
            Clock clock
    ) {
        this.integrationCommandService = integrationCommandService;
        this.jobCommandService = jobCommandService;
        this.worker = worker;
        this.clock = clock;
    }

    @Transactional
    public <T> T mutate(
            Long accountId,
            Supplier<T> authorizedCanonicalMutation,
            OutboundJobDraft jobDraft
    ) {
        T result = authorizedCanonicalMutation.get();
        GoogleCalendarIntegration integration = integrationCommandService.tryLockIntegration(accountId)
                .orElse(null);
        if (integration == null) {
            return result;
        }
        jobCommandService.enqueueOperationJob(GoogleOperationJob.outbound(
                UUID.randomUUID().toString(), integration.getId(), accountId,
                integrationCommandService.allocateOperationSequence(integration), jobDraft.kind(),
                jobDraft.resourceScope(), jobDraft.resourceKey(), jobDraft.providerIdentity(),
                jobDraft.desiredPayload(), Instant.now(clock)));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.wake(accountId);
            }
        });
        return result;
    }

    public record OutboundJobDraft(
            String kind,
            String resourceScope,
            String resourceKey,
            String providerIdentity,
            String desiredPayload
    ) {
    }
}
