package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationCommandService;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Function;
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
            Function<T, OutboundJobDraft> jobDraftFrom
    ) {
        T result = authorizedCanonicalMutation.get();
        OutboundJobDraft jobDraft = jobDraftFrom.apply(result);
        GoogleCalendarIntegration integration = integrationCommandService.tryLockIntegration(accountId)
                .orElse(null);
        if (integration == null) {
            return result;
        }
        jobCommandService.enqueueOperationJob(GoogleOperationJob.outbound(
                UUID.randomUUID().toString(), integration.getId(), accountId,
                integrationCommandService.allocateOperationSequence(integration), jobDraft.kind(),
                jobDraft.scope().storedScope(), jobDraft.scope().storedKey(), jobDraft.providerIdentity(),
                jobDraft.desiredPayload(), jobDraft.desiredContentHash(), Instant.now(clock)));
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
            GoogleCalendarEffectiveScope scope,
            String providerIdentity,
            String desiredPayload,
            String desiredContentHash
    ) {
    }
}
