package com.calio.calendar.integration.connection.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import com.calio.calendar.integration.connection.controller.dto.GoogleCalendarIntegrationResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobCommandService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.security.TokenEncryptor;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GoogleCalendarConnectionService {
    private final GoogleOAuthProperties properties;
    private final GoogleOAuthClient oauthClient;
    private final TokenEncryptor encryptor;
    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleCalendarConnectionQueryService connectionQueryService;
    private final GoogleCalendarConnectionCommandService connectionCommandService;
    private final GoogleOperationJobCommandService jobCommandService;
    private final GoogleOperationJobEnqueueService enqueueService;
    private final TransactionTemplate registrationTransaction;
    private final TransactionTemplate disconnectTransaction;
    private final Clock clock;

    public GoogleCalendarConnectionService(GoogleOAuthProperties properties, GoogleOAuthClient oauthClient,
            TokenEncryptor encryptor, GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleCalendarConnectionQueryService connectionQueryService,
            GoogleCalendarConnectionCommandService connectionCommandService,
            GoogleOperationJobCommandService jobCommandService, GoogleOperationJobEnqueueService enqueueService,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.properties = properties; this.oauthClient = oauthClient; this.encryptor = encryptor;
        this.integrationCommandService = integrationCommandService;
        this.connectionQueryService = connectionQueryService; this.connectionCommandService = connectionCommandService;
        this.jobCommandService = jobCommandService; this.enqueueService = enqueueService; this.clock = clock;
        registrationTransaction = new TransactionTemplate(transactionManager);
        registrationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        disconnectTransaction = new TransactionTemplate(transactionManager);
    }

    public GoogleCalendarIntegrationResponse connect(Long accountId, String authorizationCode) {
        if (!properties.isConfigured()) throw new CalioException(ErrorCode.GOOGLE_CALENDAR_CONFIGURATION_MISSING);
        Instant now = Instant.now(clock);
        GoogleTokenResponse token = oauthClient.exchangeAuthorizationCode(authorizationCode);
        GoogleUserInfoResponse user = oauthClient.fetchUserInfo(token.accessToken());
        GoogleCalendarConnection connection = register(accountId, user, token, now.plusSeconds(token.expiresIn()), now);
        enqueueService.enqueueManualSync(accountId);
        return GoogleCalendarIntegrationResponse.connected(connection);
    }

    public GoogleCalendarIntegrationResponse getConnectionStatus(Long accountId) {
        return connectionQueryService.findConnectedConnection(accountId)
                .map(GoogleCalendarIntegrationResponse::connected)
                .orElseGet(GoogleCalendarIntegrationResponse::disconnected);
    }

    public void disconnect(Long accountId) {
        String encrypted = disconnectTransaction.execute(status -> connectionCommandService.findConnectedConnectionForUpdate(accountId)
                .map(connection -> {
                    String refreshToken = connection.getEncryptedRefreshToken();
                    jobCommandService.deleteJobsForConnection(connection.getId());
                    connectionCommandService.disconnect(connection, Instant.now(clock));
                    return refreshToken;
                }).orElse(null));
        if (encrypted != null) revokeTokenSafely(encrypted);
    }

    private GoogleCalendarConnection register(Long accountId, GoogleUserInfoResponse user, GoogleTokenResponse token,
            Instant expiresAt, Instant connectedAt) {
        try { return registrationTransaction.execute(s -> registerInTransaction(accountId, user, token, expiresAt, connectedAt)); }
        catch (DataIntegrityViolationException ignored) { return registrationTransaction.execute(s -> registerInTransaction(accountId, user, token, expiresAt, connectedAt)); }
    }

    private GoogleCalendarConnection registerInTransaction(Long accountId, GoogleUserInfoResponse user, GoogleTokenResponse token,
            Instant expiresAt, Instant connectedAt) {
        GoogleCalendarConnection current = connectionCommandService.findSingleConnectionForUpdate(accountId).orElse(null);
        if (current != null) {
            if (!current.getGoogleSubject().equals(user.subject())) throw new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED);
            current.replaceCredentials(user.email(), encryptor.encryptRefreshToken(token.refreshToken()),
                    encryptor.encryptAccessToken(token.accessToken()), expiresAt, connectedAt);
            return current;
        }
        GoogleCalendarIntegration integration = integrationCommandService.findIntegrationForUpdate(accountId)
                .orElseGet(() -> integrationCommandService.createIntegration(accountId));
        return connectionCommandService.createConnection(integration, user.subject(), user.email(),
                encryptor.encryptRefreshToken(token.refreshToken()), encryptor.encryptAccessToken(token.accessToken()), expiresAt, connectedAt);
    }

    private void revokeTokenSafely(String encryptedRefreshToken) {
        try { oauthClient.revokeToken(encryptor.decrypt(encryptedRefreshToken)); } catch (RuntimeException ignored) { }
    }
}
