package com.calio.calendar.integration.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
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

    private final AccountRepository accountRepository;
    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleOperationJobRepository jobRepository;
    private final GoogleOperationWorker worker;
    private final Clock clock;

    public GoogleOperationProducerTransaction(
            AccountRepository accountRepository,
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleOperationJobRepository jobRepository,
            GoogleOperationWorker worker,
            Clock clock
    ) {
        this.accountRepository = accountRepository;
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
        GoogleCalendarIntegration integration = integrationRepository.findByAccountId(accountId)
                .orElse(null);
        if (integration == null) {
            return result;
        }
        Account account = accountRepository.findByIdForGoogleOperation(accountId)
                .orElseThrow();
        jobRepository.saveAndFlush(GoogleOperationJob.outbound(
                UUID.randomUUID().toString(), integration.getId(), accountId,
                account.allocateGoogleOperationSequence(), jobDraft.kind(),
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
