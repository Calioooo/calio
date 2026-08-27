package com.calio.calendar.integration.connection.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.repository.GoogleCalendarConnectionRepository;
import com.calio.calendar.integration.sync.operation.GoogleOperationOwnershipLostException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GoogleCalendarConnectionCommandService {
    private final GoogleCalendarConnectionRepository connectionRepository;

    public GoogleCalendarConnectionCommandService(GoogleCalendarConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    public GoogleCalendarConnection lockConnectedConnection(Long accountId) {
        return connectionRepository.findConnectedByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
    }

    public Optional<GoogleCalendarConnection> findConnectedConnectionForUpdate(Long accountId) {
        return connectionRepository.findConnectedByAccountIdForUpdate(accountId);
    }

    public Optional<GoogleCalendarConnection> findSingleConnectionForUpdate(Long accountId) {
        return connectionRepository.findSingleConnectionByAccountIdForUpdate(accountId);
    }

    public GoogleCalendarConnection lockConnectedConnectionById(Long connectionId) {
        return connectionRepository.findByIdForUpdate(connectionId)
                .filter(GoogleCalendarConnection::isConnected)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
    }

    public GoogleCalendarConnection createConnection(
            GoogleCalendarIntegration integration, String googleSubject, String googleEmail,
            String encryptedRefreshToken, String encryptedAccessToken, Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        return connectionRepository.saveAndFlush(new GoogleCalendarConnection(
                integration, googleSubject, googleEmail, encryptedRefreshToken, encryptedAccessToken,
                accessTokenExpiresAt, connectedAt
        ));
    }

    public void disconnect(GoogleCalendarConnection connection, Instant disconnectedAt) {
        connection.disconnect(disconnectedAt);
        connectionRepository.saveAndFlush(connection);
    }

    public void replaceAccessToken(GoogleCalendarConnection connection, String encryptedAccessToken, Instant expiresAt) {
        connection.replaceAccessToken(encryptedAccessToken, expiresAt);
        connectionRepository.saveAndFlush(connection);
    }

    public void changeNextSyncToken(GoogleCalendarConnection connection, String nextSyncToken) {
        connection.replaceNextSyncToken(nextSyncToken);
        connectionRepository.saveAndFlush(connection);
    }

    public void markConnectedConnectionSyncError(Long accountId, String reason, Instant occurredAt) {
        findConnectedConnectionForUpdate(accountId).ifPresent(connection -> {
            connection.markSyncError(reason, occurredAt);
            connectionRepository.saveAndFlush(connection);
        });
    }

    public long allocateOperationSequence(GoogleCalendarConnection connection) {
        return connection.allocateGoogleOperationSequence();
    }

    public boolean acquireOperationLease(Long accountId, String ownerToken, long seconds) {
        return connectionRepository.acquireGoogleOperationLease(accountId, ownerToken, seconds) == 1;
    }

    public void extendOperationLease(Long jobId, Long accountId, String ownerToken, long seconds) {
        if (connectionRepository.renewOwnedGoogleOperationLease(jobId, accountId, ownerToken, seconds) != 1) {
            throw new GoogleOperationOwnershipLostException();
        }
    }

    public boolean releaseOperationLease(Long accountId, String ownerToken) {
        return connectionRepository.releaseGoogleOperationLease(accountId, ownerToken) == 1;
    }
}
