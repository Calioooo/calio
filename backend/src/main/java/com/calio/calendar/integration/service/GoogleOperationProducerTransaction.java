package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
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

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleOperationJobRepository jobRepository;
    private final GoogleOperationWorker worker;
    private final Clock clock;

    public GoogleOperationProducerTransaction(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleOperationJobRepository jobRepository,
            GoogleOperationWorker worker,
            Clock clock
    ) {
        this.integrationRepository = integrationRepository;
        this.jobRepository = jobRepository;
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
        GoogleCalendarIntegration integration = integrationRepository.findByAccountIdForUpdate(accountId)
                .orElse(null);
        if (integration == null) {
            return result;
        }
        jobRepository.saveAndFlush(GoogleOperationJob.outbound(
                UUID.randomUUID().toString(), integration.getId(), accountId,
                integration.allocateGoogleOperationSequence(), jobDraft.kind(),
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
