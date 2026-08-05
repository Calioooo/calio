package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.domain.GoogleCalendarSyncTarget;
import com.calio.calendar.integration.domain.GoogleContentHash;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
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
    private final GoogleCalendarMappingLockService mappingLockService;

    public GoogleOperationProducerTransaction(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleOperationJobRepository jobRepository,
            GoogleOperationWorker worker,
            Clock clock,
            GoogleCalendarMappingLockService mappingLockService
    ) {
        this.integrationRepository = integrationRepository;
        this.jobRepository = jobRepository;
        this.worker = worker;
        this.clock = clock;
        this.mappingLockService = mappingLockService;
    }

    @Transactional
    public <T> T create(
            Long accountId,
            Supplier<T> authorizedCanonicalCreation,
            Function<T, OutboundJobDraft> jobDraftFactory
    ) {
        GoogleCalendarIntegration integration = integrationRepository
                .findByAccountIdForUpdate(accountId)
                .orElse(null);
        T createdResource = authorizedCanonicalCreation.get();
        if (integration != null) {
            enqueue(accountId, integration, jobDraftFactory.apply(createdResource));
        }
        return createdResource;
    }

    @Transactional
    public <T> T mutate(
            Long accountId,
            GoogleCalendarSyncTarget syncTarget,
            Supplier<T> authorizedCanonicalMutation,
            Function<T, OutboundJobDraft> jobDraftFactory
    ) {
        GoogleCalendarIntegration observedIntegration = integrationRepository
                .findByAccountId(accountId)
                .orElse(null);
        if (observedIntegration == null) {
            return authorizedCanonicalMutation.get();
        }
        boolean mappingConflicted = mappingLockService.isTargetConflictedAfterLocking(
                observedIntegration.getId(), syncTarget);
        GoogleCalendarIntegration integration = integrationRepository
                .findByAccountIdForUpdate(accountId)
                .filter(current -> current.getId().equals(observedIntegration.getId()))
                .orElse(null);
        T mutatedResource = authorizedCanonicalMutation.get();
        if (integration != null && !mappingConflicted) {
            OutboundJobDraft jobDraft = jobDraftFactory.apply(mutatedResource);
            if (!syncTarget.equals(jobDraft.syncTarget())) {
                throw new IllegalArgumentException(
                        "Outbound job target must match the locked mapping target"
                );
            }
            enqueue(accountId, integration, jobDraft);
        }
        return mutatedResource;
    }

    private void enqueue(
            Long accountId,
            GoogleCalendarIntegration integration,
            OutboundJobDraft jobDraft
    ) {
        jobRepository.saveAndFlush(GoogleOperationJob.outbound(
                UUID.randomUUID().toString(), integration.getId(), accountId,
                integration.allocateGoogleOperationSequence(), jobDraft.kind(),
                jobDraft.syncTarget(), jobDraft.providerIdentity(),
                jobDraft.desiredPayload(), jobDraft.desiredGoogleContentHash(),
                Instant.now(clock)));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.wake(accountId);
            }
        });
    }

    public record OutboundJobDraft(
            String kind,
            GoogleCalendarSyncTarget syncTarget,
            String providerIdentity,
            String desiredPayload,
            String desiredGoogleContentHash
    ) {
        public OutboundJobDraft {
            GoogleContentHash.requireValid(desiredGoogleContentHash);
        }
    }
}
