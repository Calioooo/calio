package com.calio.calendar.integration.connection.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarIntegrationTest {

    private static final Instant CONNECTED_AT = Instant.parse("2026-08-23T00:00:00Z");

    @Test
    @DisplayName("account 전체 실행 오류로 전이하면 credential을 유지한 채 provider 실행을 중단한다")
    void givenConnectedIntegration_whenMarkSyncError_thenPausesProviderExecutionWithoutDiscardingCredentials() {
        // given
        GoogleCalendarIntegration integration = connectedIntegration();

        // when
        integration.markSyncError("ACCOUNT_WIDE_INVARIANT_VIOLATION", CONNECTED_AT.plusSeconds(10));

        // then
        assertThat(integration.getState()).isEqualTo(GoogleCalendarIntegrationState.SYNC_ERROR);
        assertThat(integration.isConnected()).isFalse();
        assertThat(integration.getEncryptedRefreshToken()).isEqualTo("encrypted-refresh-token");
        assertThat(integration.getEncryptedAccessToken()).isEqualTo("encrypted-access-token");
        assertThat(integration.getSyncErrorReason()).isEqualTo("ACCOUNT_WIDE_INVARIANT_VIOLATION");
        assertThat(integration.getSyncErrorAt()).isEqualTo(CONNECTED_AT.plusSeconds(10));
    }

    private GoogleCalendarIntegration connectedIntegration() {
        return new GoogleCalendarIntegration(
                1L,
                "google-subject",
                "user@example.com",
                "encrypted-refresh-token",
                "encrypted-access-token",
                CONNECTED_AT.plusSeconds(3600),
                CONNECTED_AT
        );
    }
}
