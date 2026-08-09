package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import com.calio.calendar.integration.controller.dto.GoogleCalendarIntegrationResponse;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.security.TokenEncryptor;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GoogleCalendarConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarConnectionService.class);

    private final GoogleOAuthProperties googleOAuthProperties;
    private final GoogleOAuthClient googleOAuthClient;
    private final TokenEncryptor tokenEncryptor;
    private final GoogleCalendarIntegrationQueryService integrationQueryService;
    private final GoogleCalendarIntegrationCommandService integrationCommandService;
    private final GoogleCalendarIntegrationDataService integrationDataService;
    private final GoogleOperationJobCommandService jobCommandService;
    private final TransactionTemplate registrationTransaction;
    private final TransactionTemplate removalTransaction;
    private final Clock clock;

    public GoogleCalendarConnectionService(
            GoogleOAuthProperties googleOAuthProperties,
            GoogleOAuthClient googleOAuthClient,
            TokenEncryptor tokenEncryptor,
            GoogleCalendarIntegrationQueryService integrationQueryService,
            GoogleCalendarIntegrationCommandService integrationCommandService,
            GoogleCalendarIntegrationDataService integrationDataService,
            GoogleOperationJobCommandService jobCommandService,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.googleOAuthProperties = googleOAuthProperties;
        this.googleOAuthClient = googleOAuthClient;
        this.tokenEncryptor = tokenEncryptor;
        this.integrationQueryService = integrationQueryService;
        this.integrationCommandService = integrationCommandService;
        this.integrationDataService = integrationDataService;
        this.jobCommandService = jobCommandService;
        this.registrationTransaction = new TransactionTemplate(transactionManager);
        this.registrationTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
        this.removalTransaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public GoogleCalendarIntegrationResponse connect(Long accountId, String authorizationCode) {
        validateConfiguration();

        Instant tokenReceivedAt = Instant.now(clock);
        GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeAuthorizationCode(authorizationCode);
        GoogleUserInfoResponse userInfo = googleOAuthClient.fetchUserInfo(tokenResponse.accessToken());
        EncryptedGoogleTokens encryptedTokens = encryptTokens(tokenResponse);
        Instant accessTokenExpiresAt = tokenReceivedAt.plusSeconds(tokenResponse.expiresIn());

        GoogleCalendarIntegration integration = registerConnection(
                accountId,
                userInfo,
                encryptedTokens,
                accessTokenExpiresAt,
                tokenReceivedAt
        );
        return GoogleCalendarIntegrationResponse.connected(integration);
    }

    public GoogleCalendarIntegrationResponse getConnectionStatus(Long accountId) {
        return integrationQueryService.getIntegrationIfExists(accountId)
                .map(GoogleCalendarIntegrationResponse::connected)
                .orElseGet(GoogleCalendarIntegrationResponse::disconnected);
    }

    public void disconnect(Long accountId) {
        Optional<GoogleCalendarIntegration> integration =
                integrationQueryService.getIntegrationIfExists(accountId);
        if (integration.isEmpty()) {
            return;
        }

        String refreshToken = tokenEncryptor.decrypt(
                integration.get().getEncryptedRefreshToken()
        );
        googleOAuthClient.revokeToken(refreshToken);
        removeLocalConnection(accountId);
    }

    private void validateConfiguration() {
        if (!googleOAuthProperties.isConfigured()) {
            logIntegrationFailure("validateConfiguration", ErrorCode.GOOGLE_CALENDAR_CONFIGURATION_MISSING);
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_CONFIGURATION_MISSING);
        }
    }

    private EncryptedGoogleTokens encryptTokens(GoogleTokenResponse tokenResponse) {
        return new EncryptedGoogleTokens(
                tokenEncryptor.encryptRefreshToken(tokenResponse.refreshToken()),
                tokenEncryptor.encryptAccessToken(tokenResponse.accessToken())
        );
    }

    private GoogleCalendarIntegration registerConnection(
            Long accountId,
            GoogleUserInfoResponse userInfo,
            EncryptedGoogleTokens encryptedTokens,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
        GoogleCalendarConnectionCredentials credentials =
                new GoogleCalendarConnectionCredentials(
                        accountId,
                        userInfo.subject(),
                        userInfo.email(),
                        encryptedTokens.refreshToken(),
                        encryptedTokens.accessToken(),
                        accessTokenExpiresAt,
                        connectedAt
                );
        try {
            return register(credentials);
        } catch (DataIntegrityViolationException exception) {
            return retryRegistrationAfterRace(credentials);
        }
    }

    private GoogleCalendarIntegration retryRegistrationAfterRace(
            GoogleCalendarConnectionCredentials credentials
    ) {
        try {
            return register(credentials);
        } catch (DataIntegrityViolationException exception) {
            logIntegrationFailure(
                    "registerConnection",
                    ErrorCode.GOOGLE_CALENDAR_INTEGRATION_SAVE_FAILED,
                    exception
            );
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_INTEGRATION_SAVE_FAILED, exception);
        }
    }

    private void logIntegrationFailure(String operation, ErrorCode errorCode) {
        log.warn(
                "Google Calendar integration failure. operation={} errorCode={}",
                operation,
                errorCode.name()
        );
    }

    private void logIntegrationFailure(String operation, ErrorCode errorCode, Exception exception) {
        log.warn(
                "Google Calendar integration failure. operation={} errorCode={} causeType={}",
                operation,
                errorCode.name(),
                exception.getClass().getSimpleName()
        );
    }

    private GoogleCalendarIntegration register(
            GoogleCalendarConnectionCredentials credentials
    ) {
        return registrationTransaction.execute(status -> createOrReplace(credentials));
    }

    private GoogleCalendarIntegration createOrReplace(
            GoogleCalendarConnectionCredentials credentials
    ) {
        return integrationCommandService.tryLockIntegration(credentials.accountId())
                .map(integration -> replaceConnection(integration, credentials))
                .orElseGet(() -> createConnection(credentials));
    }

    private GoogleCalendarIntegration createConnection(
            GoogleCalendarConnectionCredentials credentials
    ) {
        return integrationCommandService.createIntegration(
                credentials.accountId(),
                credentials.googleSubject(),
                credentials.googleEmail(),
                credentials.encryptedRefreshToken(),
                credentials.encryptedAccessToken(),
                credentials.accessTokenExpiresAt(),
                credentials.connectedAt()
        );
    }

    private GoogleCalendarIntegration replaceConnection(
            GoogleCalendarIntegration integration,
            GoogleCalendarConnectionCredentials credentials
    ) {
        integrationDataService.deleteIntegrationData(integration.getId());
        return integrationCommandService.replaceIntegration(
                integration,
                credentials.googleSubject(),
                credentials.googleEmail(),
                credentials.encryptedRefreshToken(),
                credentials.encryptedAccessToken(),
                credentials.accessTokenExpiresAt(),
                credentials.connectedAt()
        );
    }

    private void removeLocalConnection(Long accountId) {
        removalTransaction.executeWithoutResult(status ->
                integrationCommandService.tryLockIntegration(accountId)
                        .ifPresent(this::removeConnection));
    }

    private void removeConnection(GoogleCalendarIntegration integration) {
        jobCommandService.deleteJobsForIntegration(integration.getId());
        integrationDataService.deleteIntegrationData(integration.getId());
        integrationCommandService.deleteIntegration(integration);
    }

    private record EncryptedGoogleTokens(
            String refreshToken,
            String accessToken
    ) {
    }

    private record GoogleCalendarConnectionCredentials(
            Long accountId,
            String googleSubject,
            String googleEmail,
            String encryptedRefreshToken,
            String encryptedAccessToken,
            Instant accessTokenExpiresAt,
            Instant connectedAt
    ) {
    }
}
