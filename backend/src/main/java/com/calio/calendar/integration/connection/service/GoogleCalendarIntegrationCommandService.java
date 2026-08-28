package com.calio.calendar.integration.connection.service;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.sync.operation.GoogleOperationOwnershipLostException;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarIntegrationCommandService {
    private final GoogleCalendarIntegrationRepository integrationRepository;

    public GoogleCalendarIntegrationCommandService(GoogleCalendarIntegrationRepository integrationRepository) {
        this.integrationRepository = integrationRepository;
    }

    public Optional<GoogleCalendarIntegration> tryLockIntegration(Long accountId) {
        return integrationRepository.findByAccountIdForUpdate(accountId);
    }

    public GoogleCalendarIntegration createIntegration(Long accountId) {
        return integrationRepository.saveAndFlush(new GoogleCalendarIntegration(accountId));
    }

    public boolean acquireOperationLease(Long accountId, String ownerToken, long seconds) {
        return integrationRepository.acquireGoogleOperationLease(accountId, ownerToken, seconds) == 1;
    }

    public void extendOperationLease(Long jobId, Long accountId, String ownerToken, long seconds) {
        if (integrationRepository.renewOwnedGoogleOperationLease(jobId, accountId, ownerToken, seconds) != 1) {
            throw new GoogleOperationOwnershipLostException();
        }
    }

    public boolean releaseOperationLease(Long accountId, String ownerToken) {
        return integrationRepository.releaseGoogleOperationLease(accountId, ownerToken) == 1;
    }
}
