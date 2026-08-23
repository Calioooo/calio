package com.calio.calendar.integration.connection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarInvalidGrantException;
import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.security.TokenEncryptor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarAccessTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");

    private final GoogleCalendarIntegrationQueryService integrationQueryService =
            mock(GoogleCalendarIntegrationQueryService.class);
    private final GoogleCalendarIntegrationCommandService integrationCommandService =
            mock(GoogleCalendarIntegrationCommandService.class);
    private final GoogleOAuthClient googleOAuthClient = mock(GoogleOAuthClient.class);
    private final TokenEncryptor tokenEncryptor = mock(TokenEncryptor.class);
    private final GoogleCalendarIntegrationLifecycleService lifecycleService =
            mock(GoogleCalendarIntegrationLifecycleService.class);
    private final GoogleCalendarAccessTokenService service = new GoogleCalendarAccessTokenService(
            integrationQueryService,
            integrationCommandService,
            googleOAuthClient,
            tokenEncryptor,
            lifecycleService,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    @DisplayName("confirmed invalid_grant는 retained disconnect 후 재연결 필요 오류를 반환한다")
    void givenInvalidGrantDuringRefresh_whenForceRefresh_thenDisconnectsRetainedIntegration() {
        // given
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        when(integration.getId()).thenReturn(10L);
        when(integration.getEncryptedRefreshToken()).thenReturn("encrypted-refresh-token");
        when(integration.getEncryptedAccessToken()).thenReturn("encrypted-access-token");
        when(integration.getAccessTokenExpiresAt()).thenReturn(NOW.plusSeconds(3600));
        when(integrationQueryService.getIntegrationById(10L)).thenReturn(integration);
        when(tokenEncryptor.decrypt("encrypted-refresh-token")).thenReturn("refresh-token");
        when(googleOAuthClient.refreshAccessToken("refresh-token"))
                .thenThrow(new GoogleCalendarInvalidGrantException(new RuntimeException("invalid_grant")));

        // when, then
        assertThatThrownBy(() -> service.forceRefresh(10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED));
        verify(lifecycleService).disconnectAfterInvalidGrant(10L, NOW);
    }
}
