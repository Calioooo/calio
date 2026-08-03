package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarIntegrationState;
import com.calio.calendar.integration.domain.GoogleCalendarOperationJob;
import com.calio.calendar.integration.domain.GoogleCalendarOperationKind;
import com.calio.calendar.integration.domain.GoogleCalendarOperationScope;
import com.calio.calendar.integration.domain.GoogleCalendarOperationTrigger;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarOperationJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GoogleCalendarMutationIntentProducer {

    private final GoogleCalendarIntegrationRepository integrationRepository;
    private final GoogleCalendarOperationJobRepository jobRepository;
    private final GoogleCalendarOperationWakeup wakeup;
    private final Clock clock;

    public GoogleCalendarMutationIntentProducer(
            GoogleCalendarIntegrationRepository integrationRepository,
            GoogleCalendarOperationJobRepository jobRepository,
            GoogleCalendarOperationWakeup wakeup,
            Clock clock
    ) {
        this.integrationRepository = integrationRepository;
        this.jobRepository = jobRepository;
        this.wakeup = wakeup;
        this.clock = clock;
    }

    @Transactional
    public Optional<String> persistIntent(
            Long accountId,
            GoogleCalendarOperationKind kind,
            GoogleCalendarOperationScope scope,
            String scopeKey,
            String narrowDesiredPayload,
            String providerIdentity
    ) {
        if (kind == GoogleCalendarOperationKind.SYNC) {
            throw new IllegalArgumentException("Mutation producer does not accept SYNC operations");
        }
        GoogleCalendarIntegration integration = integrationRepository
                .findByAccountIdForUpdate(accountId)
                .orElse(null);
        if (integration == null
                || integration.getState() == GoogleCalendarIntegrationState.DISCONNECTED) {
            return Optional.empty();
        }
        GoogleCalendarOperationJob job = jobRepository.save(new GoogleCalendarOperationJob(
                integration,
                integration.allocateOperationSequence(),
                kind,
                GoogleCalendarOperationTrigger.CANONICAL_MUTATION,
                scope,
                scopeKey,
                narrowDesiredPayload,
                providerIdentity,
                Instant.now(clock)
        ));
        if (integration.isConnected()) {
            registerAfterCommitWakeup();
        }
        return Optional.of(job.getOperationId());
    }

    private void registerAfterCommitWakeup() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                wakeup.wakeUp();
            }
        });
    }
}
