package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.domain.GoogleCalendarEffectiveScope;
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
    private final GoogleCalendarMappingLockCoordinator mappingLockCoordinator;

    public GoogleOperationProducerTransaction(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleOperationJobRepository jobRepository,
            GoogleOperationWorker worker,
            Clock clock,
            GoogleCalendarMappingLockCoordinator mappingLockCoordinator
    ) {
        this.integrationRepository = integrationRepository;
        this.jobRepository = jobRepository;
        this.worker = worker;
        this.clock = clock;
        this.mappingLockCoordinator = mappingLockCoordinator;
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
            GoogleCalendarEffectiveScope effectiveScope,
            Supplier<T> authorizedCanonicalMutation,
            Function<T, OutboundJobDraft> jobDraftFactory
    ) {
        GoogleCalendarIntegration observedIntegration = integrationRepository
                .findByAccountId(accountId)
                .orElse(null);
        if (observedIntegration == null) {
            return authorizedCanonicalMutation.get();
        }
        boolean isConflicted = mappingLockCoordinator.isConflictedAfterLock(
                observedIntegration.getId(), effectiveScope);
        GoogleCalendarIntegration integration = integrationRepository
                .findByAccountIdForUpdate(accountId)
                .filter(current -> current.getId().equals(observedIntegration.getId()))
                .orElse(null);
        T mutatedResource = authorizedCanonicalMutation.get();
        if (integration != null && !isConflicted) {
            OutboundJobDraft jobDraft = jobDraftFactory.apply(mutatedResource);
            if (!effectiveScope.equals(jobDraft.effectiveScope())) {
                throw new IllegalArgumentException(
                        "Outbound Job scope must match the locked mapping scope"
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
                jobDraft.effectiveScope(), jobDraft.providerIdentity(),
                jobDraft.desiredPayload(), jobDraft.desiredContentHash(), Instant.now(clock)));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                worker.wake(accountId);
            }
        });
    }

    public record OutboundJobDraft(
            String kind,
            GoogleCalendarEffectiveScope effectiveScope,
            String providerIdentity,
            String desiredPayload,
            String desiredContentHash
    ) {
        public OutboundJobDraft {
            GoogleContentHash.requireValid(desiredContentHash);
        }
    }
}
