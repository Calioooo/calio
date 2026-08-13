package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationCommandService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleOperationLeaseService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOperationLeaseService.class);
    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);

    private final GoogleCalendarIntegrationCommandService integrationCommandService;

    public GoogleOperationLeaseService(
            GoogleCalendarIntegrationCommandService integrationCommandService
    ) {
        this.integrationCommandService = integrationCommandService;
    }

    @Transactional
    public boolean acquire(Long accountId, String workerToken) {
        boolean acquired = integrationCommandService.acquireOperationLease(
                accountId,
                workerToken,
                LEASE_DURATION.toSeconds()
        );
        if (acquired) {
            log.info("Google operation lease acquired. accountId={} state=ACQUIRED", accountId);
        } else {
            log.warn("Google operation lease acquisition failed. accountId={} state=NOT_ACQUIRED", accountId);
        }
        return acquired;
    }

    @Transactional
    public void extend(Long jobId, Long accountId, String workerToken) {
        try {
            integrationCommandService.extendOperationLease(
                    jobId,
                    accountId,
                    workerToken,
                    LEASE_DURATION.toSeconds()
            );
        } catch (GoogleOperationOwnershipLostException exception) {
            log.warn(
                    "Google operation ownership lost. accountId={} jobId={} state=PROCESSING",
                    accountId,
                    jobId
            );
            throw exception;
        }
    }

    @Transactional
    public void release(Long accountId, String workerToken) {
        boolean released = integrationCommandService.releaseOperationLease(accountId, workerToken);
        if (released) {
            log.info("Google operation lease released. accountId={} state=RELEASED", accountId);
        } else {
            log.warn("Google operation lease release skipped. accountId={} state=NOT_OWNED", accountId);
        }
    }
}
