package com.calio.calendar.integration.connection.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.repository.GoogleCalendarConnectionRepository;
import com.calio.calendar.integration.sync.operation.GoogleOperationOwnershipLostException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarConnectionCommandService {
    private final GoogleCalendarConnectionRepository connectionRepository;

    public GoogleCalendarConnectionCommandService(GoogleCalendarConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    public GoogleCalendarConnection lockConnectedConnection(Long accountId) {
        return connectionRepository.findWithIntegrationByAccountIdAndStateForUpdate(
                        accountId, GoogleCalendarConnectionState.CONNECTED
                )
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
    }

    public Optional<GoogleCalendarConnection> tryLockConnectedConnection(Long accountId) {
        return connectionRepository.findWithIntegrationByAccountIdAndStateForUpdate(
                accountId, GoogleCalendarConnectionState.CONNECTED
        );
    }

    public Optional<GoogleCalendarConnection> tryLockConnection(
            Long integrationId,
            String googleSubject
    ) {
        return connectionRepository.findWithIntegrationByIntegrationIdAndGoogleSubjectForUpdate(
                integrationId,
                googleSubject
        );
    }

    public Optional<GoogleCalendarConnection> tryLockConnectionByIntegrationAndState(
            Long integrationId,
            GoogleCalendarConnectionState state
    ) {
        return connectionRepository.findWithIntegrationByIntegrationIdAndStateForUpdate(integrationId, state);
    }

    public GoogleCalendarConnection lockConnectedConnectionById(Long connectionId) {
        return tryLockConnectedConnectionById(connectionId)
                .orElseThrow(() -> new CalioException(ErrorCode.GOOGLE_CALENDAR_NOT_CONNECTED));
    }

    public Optional<GoogleCalendarConnection> tryLockConnectedConnectionById(Long connectionId) {
        return connectionRepository.findWithIntegrationByIdForUpdate(connectionId)
                .filter(GoogleCalendarConnection::isConnected);
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

    public void replaceCredentials(
            GoogleCalendarConnection connection,
            String googleEmail,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        connection.replaceCredentials(
                googleEmail,
                encryptedRefreshToken,
                encryptedAccessToken,
                accessTokenExpiresAt,
                connectedAt
        );
        connectionRepository.saveAndFlush(connection);
    }

    public void changeNextSyncToken(GoogleCalendarConnection connection, String nextSyncToken) {
        connection.replaceNextSyncToken(nextSyncToken);
        connectionRepository.saveAndFlush(connection);
    }

    public void markConnectedConnectionSyncError(Long accountId, String reason, Instant occurredAt) {
        tryLockConnectedConnection(accountId).ifPresent(connection -> {
            connection.markSyncError(reason, occurredAt);
            connectionRepository.saveAndFlush(connection);
        });
    }

}
