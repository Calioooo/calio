package com.calio.calendar.integration.connection.service;

import com.calio.calendar.account.service.AccountCommandService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import com.calio.calendar.integration.connection.controller.dto.GoogleCalendarConnectionResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobCommandService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.security.TokenEncryptor;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GoogleCalendarConnectionService {
  private final GoogleOAuthProperties properties;
  private final GoogleOAuthClient oauthClient;
  private final TokenEncryptor encryptor;
  private final AccountCommandService accountCommandService;
  private final GoogleCalendarIntegrationCommandService integrationCommandService;
  private final GoogleCalendarConnectionQueryService connectionQueryService;
  private final GoogleCalendarConnectionCommandService connectionCommandService;
  private final GoogleCalendarTokenRevocationService tokenRevocationService;
  private final GoogleOperationJobCommandService jobCommandService;
  private final GoogleOperationJobEnqueueService enqueueService;
  private final TransactionTemplate registrationTransaction;
  private final TransactionTemplate disconnectTransaction;
  private final Clock clock;

  public GoogleCalendarConnectionService(
      GoogleOAuthProperties properties,
      GoogleOAuthClient oauthClient,
      TokenEncryptor encryptor,
      AccountCommandService accountCommandService,
      GoogleCalendarIntegrationCommandService integrationCommandService,
      GoogleCalendarConnectionQueryService connectionQueryService,
      GoogleCalendarConnectionCommandService connectionCommandService,
      GoogleCalendarTokenRevocationService tokenRevocationService,
      GoogleOperationJobCommandService jobCommandService,
      GoogleOperationJobEnqueueService enqueueService,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.properties = properties;
    this.oauthClient = oauthClient;
    this.encryptor = encryptor;
    this.accountCommandService = accountCommandService;
    this.integrationCommandService = integrationCommandService;
    this.connectionQueryService = connectionQueryService;
    this.connectionCommandService = connectionCommandService;
    this.tokenRevocationService = tokenRevocationService;
    this.jobCommandService = jobCommandService;
    this.enqueueService = enqueueService;
    this.clock = clock;
    registrationTransaction = new TransactionTemplate(transactionManager);
    registrationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    disconnectTransaction = new TransactionTemplate(transactionManager);
  }

  public GoogleCalendarConnectionResponse connect(Long accountId, String authorizationCode) {
    if (!properties.isConfigured()) {
      throw new CalioException(ErrorCode.GOOGLE_CALENDAR_CONFIGURATION_MISSING);
    }
    Instant now = Instant.now(clock);
    GoogleTokenResponse token = oauthClient.exchangeAuthorizationCode(authorizationCode);
    GoogleUserInfoResponse user = oauthClient.fetchUserInfo(token.accessToken());
    GoogleCalendarConnection connection =
        registerAndEnqueueInitialSync(
            accountId, user, token, now.plusSeconds(token.expiresIn()), now);
    return GoogleCalendarConnectionResponse.connected(connection);
  }

  public GoogleCalendarConnectionResponse getConnectionStatus(Long accountId) {
    return connectionQueryService
        .getConnectedConnectionIfExists(accountId)
        .map(GoogleCalendarConnectionResponse::connected)
        .orElseGet(GoogleCalendarConnectionResponse::disconnected);
  }

  public void disconnect(Long accountId) {
    String encryptedRefreshToken = disconnectLocally(accountId);
    tokenRevocationService.revokeSafely(accountId, encryptedRefreshToken);
  }

  private String disconnectLocally(Long accountId) {
    return disconnectTransaction.execute(
        status ->
            integrationCommandService
                .tryLockIntegration(accountId)
                .flatMap(
                    integration ->
                        connectionCommandService.tryLockDisconnectableConnectionByIntegration(
                            integration.getId()))
                .map(
                    connection -> {
                      String refreshToken = connection.getEncryptedRefreshToken();
                      jobCommandService.deleteJobsForIntegration(
                          connection.getIntegration().getId());
                      connectionCommandService.disconnect(connection, Instant.now(clock));
                      return refreshToken;
                    })
                .orElse(null));
  }

  private GoogleCalendarConnection registerAndEnqueueInitialSync(
      Long accountId,
      GoogleUserInfoResponse user,
      GoogleTokenResponse token,
      Instant expiresAt,
      Instant connectedAt) {
    return registrationTransaction.execute(
        status -> {
          accountCommandService.lockAccount(accountId);
          GoogleCalendarConnection connection =
              registerInTransaction(accountId, user, token, expiresAt, connectedAt);
          enqueueService.enqueueManualSync(accountId);
          return connection;
        });
  }

  private GoogleCalendarConnection registerInTransaction(
      Long accountId,
      GoogleUserInfoResponse user,
      GoogleTokenResponse token,
      Instant expiresAt,
      Instant connectedAt) {
    GoogleCalendarIntegration integration =
        integrationCommandService
            .tryLockIntegration(accountId)
            .orElseGet(() -> integrationCommandService.createIntegration(accountId));
    GoogleCalendarConnection active =
        connectionCommandService
            .tryLockConnectionByIntegrationAndState(
                integration.getId(), GoogleCalendarConnectionState.CONNECTED)
            .orElse(null);
    if (active != null) {
      throw new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED);
    }
    GoogleCalendarConnection retained =
        connectionCommandService
            .tryLockConnection(integration.getId(), user.subject())
            .orElse(null);
    if (retained != null) {
      connectionCommandService.replaceCredentials(
          retained,
          user.email(),
          encryptor.encryptRefreshToken(token.refreshToken()),
          encryptor.encryptAccessToken(token.accessToken()),
          expiresAt,
          connectedAt);
      return retained;
    }
    return connectionCommandService.createConnection(
        integration,
        user.subject(),
        user.email(),
        encryptor.encryptRefreshToken(token.refreshToken()),
        encryptor.encryptAccessToken(token.accessToken()),
        expiresAt,
        connectedAt);
  }
}
