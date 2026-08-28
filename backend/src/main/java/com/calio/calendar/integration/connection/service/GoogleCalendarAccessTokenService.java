package com.calio.calendar.integration.connection.service;

import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleCalendarInvalidGrantException;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.dto.GoogleAccessTokenRefreshResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobCommandService;
import com.calio.calendar.security.TokenEncryptor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GoogleCalendarAccessTokenService {

    private static final Duration REFRESH_WINDOW = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarAccessTokenService.class);

    private final GoogleCalendarConnectionQueryService connectionQueryService;
    private final GoogleCalendarConnectionCommandService connectionCommandService;
    private final GoogleOAuthClient googleOAuthClient;
    private final TokenEncryptor tokenEncryptor;
    private final GoogleOperationJobCommandService jobCommandService;
    private final TransactionTemplate disconnectTransaction;
    private final Clock clock;

    public GoogleCalendarAccessTokenService(
            GoogleCalendarConnectionQueryService connectionQueryService,
            GoogleCalendarConnectionCommandService connectionCommandService,
            GoogleOAuthClient googleOAuthClient,
            TokenEncryptor tokenEncryptor,
            GoogleOperationJobCommandService jobCommandService,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.connectionQueryService = connectionQueryService;
        this.connectionCommandService = connectionCommandService;
        this.googleOAuthClient = googleOAuthClient;
        this.tokenEncryptor = tokenEncryptor;
        this.jobCommandService = jobCommandService;
        this.disconnectTransaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public String getAccessToken(Long connectionId) {
        TokenState tokenState = readTokenState(connectionId);
        if (isUsable(tokenState.accessTokenExpiresAt())) {
            return tokenEncryptor.decrypt(tokenState.encryptedAccessToken());
        }
        return refresh(tokenState);
    }

    public String forceRefresh(Long connectionId) {
        return refresh(readTokenState(connectionId));
    }

    private String refresh(TokenState tokenState) {
        String refreshToken = tokenEncryptor.decrypt(tokenState.encryptedRefreshToken());
        GoogleAccessTokenRefreshResponse response;
        try {
            response = googleOAuthClient.refreshAccessToken(refreshToken);
        } catch (GoogleCalendarInvalidGrantException exception) {
            disconnectAfterInvalidGrant(tokenState.connectionId(), Instant.now(clock));
            log.warn("Google Calendar connection disconnected after invalid_grant. connectionId={}",
                    tokenState.connectionId());
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED, exception);
        }
        String encryptedAccessToken = tokenEncryptor.encryptAccessToken(response.accessToken());
        Instant expiresAt = Instant.now(clock).plusSeconds(response.expiresIn());
        persistRefreshedToken(tokenState, encryptedAccessToken, expiresAt);
        return response.accessToken();
    }

    private void disconnectAfterInvalidGrant(Long connectionId, Instant disconnectedAt) {
        disconnectTransaction.executeWithoutResult(status -> {
            GoogleCalendarConnection connection = connectionCommandService.lockConnectedConnectionById(connectionId);
            jobCommandService.deleteJobsForIntegration(connection.getIntegration().getId());
            connectionCommandService.disconnect(connection, disconnectedAt);
        });
    }

    private boolean isUsable(Instant expiresAt) {
        return expiresAt.isAfter(Instant.now(clock).plus(REFRESH_WINDOW));
    }

    protected TokenState readTokenState(Long connectionId) {
        GoogleCalendarConnection connection = connectionQueryService.getConnectedConnectionById(connectionId);
        return new TokenState(
                connection.getId(),
                connection.getEncryptedRefreshToken(),
                connection.getEncryptedAccessToken(),
                connection.getAccessTokenExpiresAt()
        );
    }

    protected void persistRefreshedToken(
            TokenState tokenState,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt
    ) {
        GoogleCalendarConnection connection =
                connectionCommandService.lockConnectedConnectionById(tokenState.connectionId());
        connectionCommandService.replaceAccessToken(connection, encryptedAccessToken, accessTokenExpiresAt);
    }

    protected record TokenState(
            Long connectionId,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt
    ) {
    }
}
