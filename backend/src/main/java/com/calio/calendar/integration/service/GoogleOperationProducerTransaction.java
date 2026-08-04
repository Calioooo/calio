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
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GoogleOperationProducerTransaction {

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleOperationJobRepository jobRepository;
    private final GoogleOperationWorker worker;
    private final Clock clock;
    private final GoogleMappingConflictService mappingConflictService;

    @Autowired
    public GoogleOperationProducerTransaction(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleOperationJobRepository jobRepository,
            GoogleOperationWorker worker,
            Clock clock,
            GoogleMappingConflictService mappingConflictService
    ) {
        this.integrationRepository = integrationRepository;
        this.jobRepository = jobRepository;
        this.worker = worker;
        this.clock = clock;
        this.mappingConflictService = mappingConflictService;
    }

    GoogleOperationProducerTransaction(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleOperationJobRepository jobRepository,
            GoogleOperationWorker worker,
            Clock clock
    ) {
        this(integrationRepository, jobRepository, worker, clock, null);
    }

    @Transactional
    public <T> T mutate(
            Long accountId,
            Supplier<T> authorizedCanonicalMutation,
            OutboundJobDraft jobDraft
    ) {
        GoogleCalendarIntegration integration = integrationRepository.findByAccountIdForUpdate(accountId)
                .orElse(null);
        boolean isLocalOnly = integration == null
                || mappingConflictService != null && mappingConflictService.shouldRemainLocal(
                integration.getId(), jobDraft.effectiveScope());
        T result = authorizedCanonicalMutation.get();
        if (isLocalOnly) {
            return result;
        }
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
        return result;
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

        public OutboundJobDraft(
                String kind,
                String resourceScope,
                String resourceKey,
                String providerIdentity,
                String desiredPayload
        ) {
            this(kind, decodeLegacyScope(resourceScope, resourceKey), providerIdentity,
                    desiredPayload,
                    GoogleContentHash.digest("OUTBOUND_DESIRED_PAYLOAD", desiredPayload));
        }

        private static GoogleCalendarEffectiveScope decodeLegacyScope(
                String resourceScope,
                String resourceKey
        ) {
            if ("EVENT".equals(resourceScope)) {
                return GoogleCalendarEffectiveScope.generalEvent(resourceKey);
            }
            return GoogleCalendarEffectiveScope.decode(resourceScope, resourceKey);
        }
    }
}
