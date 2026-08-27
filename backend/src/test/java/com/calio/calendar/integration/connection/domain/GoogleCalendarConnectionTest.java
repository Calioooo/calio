package com.calio.calendar.integration.connection.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarConnectionTest {

    private static final Instant CONNECTED_AT = Instant.parse("2026-08-28T00:00:00Z");

    @Test
    @DisplayName("Connection의 sync error는 credential을 유지하면서 provider 실행만 중단한다")
    void givenConnectedConnection_whenMarkSyncError_thenPreservesCredentialsAndPausesConnection() {
        GoogleCalendarConnection connection = connection();

        connection.markSyncError("ACCOUNT_WIDE_INVARIANT_VIOLATION", CONNECTED_AT.plusSeconds(10));

        assertThat(connection.getState()).isEqualTo(GoogleCalendarConnectionState.SYNC_ERROR);
        assertThat(connection.isConnected()).isFalse();
        assertThat(connection.getEncryptedRefreshToken()).isEqualTo("encrypted-refresh-token");
        assertThat(connection.getEncryptedAccessToken()).isEqualTo("encrypted-access-token");
        assertThat(connection.getSyncErrorReason()).isEqualTo("ACCOUNT_WIDE_INVARIANT_VIOLATION");
    }

    private GoogleCalendarConnection connection() {
        return new GoogleCalendarConnection(
                new GoogleCalendarIntegration(1L), "google-subject", "google@example.com",
                "encrypted-refresh-token", "encrypted-access-token", CONNECTED_AT.plusSeconds(3600), CONNECTED_AT);
    }
}
