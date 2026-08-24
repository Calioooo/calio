package com.calio.calendar.integration.connection.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.sync.operation.GoogleOperationOwnershipLostException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoogleCalendarIntegrationCommandService {

    private final GoogleCalendarIntegrationRepository integrationRepository;

    public GoogleCalendarIntegrationCommandService(
            GoogleCalendarIntegrationRepository integrationRepository
    ) {
        this.integrationRepository = integrationRepository;
    }

    public GoogleCalendarIntegration lockIntegration(Long accountId) {
        return integrationRepository.findByAccountIdForUpdate(accountId)
                .filter(GoogleCalendarIntegration::isConnected)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
    }

    public Optional<GoogleCalendarIntegration> tryLockIntegration(Long accountId) {
        return integrationRepository.findByAccountIdForUpdate(accountId);
    }

    public Optional<GoogleCalendarIntegration> tryLockIntegrationById(Long integrationId) {
        return integrationRepository.findByIdForUpdate(integrationId);
    }

    public GoogleCalendarIntegration createIntegration(
            Long accountId,
            String googleSubject,
            String googleEmail,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        return integrationRepository.saveAndFlush(new GoogleCalendarIntegration(
                accountId,
                googleSubject,
                googleEmail,
                encryptedRefreshToken,
                encryptedAccessToken,
                accessTokenExpiresAt,
                connectedAt
        ));
    }

    public GoogleCalendarIntegration replaceIntegration(
            GoogleCalendarIntegration integration,
            String googleSubject,
            String googleEmail,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        integration.replace(
                googleSubject,
                googleEmail,
                encryptedRefreshToken,
                encryptedAccessToken,
                accessTokenExpiresAt,
                connectedAt
        );
        return integrationRepository.saveAndFlush(integration);
    }

    public void disconnectIntegration(GoogleCalendarIntegration integration, Instant disconnectedAt) {
        integration.disconnect(disconnectedAt);
        integrationRepository.saveAndFlush(integration);
    }

    public void markIntegrationSyncError(
            GoogleCalendarIntegration integration,
            String reason,
            Instant occurredAt
    ) {
        integration.markSyncError(reason, occurredAt);
        integrationRepository.saveAndFlush(integration);
    }

    public void markConnectedIntegrationSyncError(
            Long accountId,
            String reason,
            Instant occurredAt
    ) {
        tryLockIntegration(accountId)
                .filter(GoogleCalendarIntegration::isConnected)
                .ifPresent(integration -> markIntegrationSyncError(integration, reason, occurredAt));
    }

    public void deleteIntegration(GoogleCalendarIntegration integration) {
        integrationRepository.delete(integration);
    }

    public void replaceAccessToken(
            Long integrationId,
            String expectedEncryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt
    ) {
        int updated = integrationRepository.updateAccessToken(
                integrationId,
                expectedEncryptedRefreshToken,
                encryptedAccessToken,
                accessTokenExpiresAt
        );
        if (updated != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED);
        }
    }

    public void changeNextSyncToken(Long integrationId, String nextSyncToken) {
        if (integrationRepository.updateNextSyncToken(integrationId, nextSyncToken) != 1) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_SYNC_CONFLICT);
        }
    }

    public boolean acquireOperationLease(
            Long accountId,
            String ownerToken,
            long leaseDurationSeconds
    ) {
        return integrationRepository.acquireGoogleOperationLease(
                accountId,
                ownerToken,
                leaseDurationSeconds
        ) == 1;
    }

    public void extendOperationLease(
            Long jobId,
            Long accountId,
            String ownerToken,
            long leaseDurationSeconds
    ) {
        if (integrationRepository.renewOwnedGoogleOperationLease(
                jobId,
                accountId,
                ownerToken,
                leaseDurationSeconds
        ) != 1) {
            throw new GoogleOperationOwnershipLostException();
        }
    }

    public boolean releaseOperationLease(Long accountId, String ownerToken) {
        return integrationRepository.releaseGoogleOperationLease(accountId, ownerToken) == 1;
    }

    public long allocateOperationSequence(GoogleCalendarIntegration integration) {
        return integration.allocateGoogleOperationSequence();
    }
}
