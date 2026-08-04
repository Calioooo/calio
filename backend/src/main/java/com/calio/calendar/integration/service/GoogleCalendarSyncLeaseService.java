package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.service.GoogleOperationJobPersistenceService.GoogleOperationOwnershipLostException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleCalendarSyncLeaseService {

    private final GoogleCalendarIntegrationRepository integrationRepository;

    public GoogleCalendarSyncLeaseService(
            GoogleCalendarIntegrationRepository integrationRepository
    ) {
        this.integrationRepository = integrationRepository;
    }

    @Transactional
    public SyncLease acquire(Long accountId, String runId) {
        if (!integrationRepository.existsByAccountId(accountId)) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED);
        }
        if (integrationRepository.acquireSyncLease(accountId, runId) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
        GoogleCalendarIntegration integration = integrationRepository.findByAccountId(accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
        return new SyncLease(
                integration.getId(),
                integration.getAccountId(),
                integration.getNextSyncToken(),
                runId
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void renewOwnedLeases(
            Long accountId,
            Long integrationId,
            String runId
    ) {
        if (integrationRepository.renewGoogleOperationLease(accountId, runId) != 1) {
            throw new GoogleOperationOwnershipLostException();
        }
        if (integrationRepository.extendSyncLease(integrationId, runId) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
    }

    public record SyncLease(
            Long integrationId,
            Long accountId,
            String nextSyncToken,
            String runId
    ) {
    }
}
