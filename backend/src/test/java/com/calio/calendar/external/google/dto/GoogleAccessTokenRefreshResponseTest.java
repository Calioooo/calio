package com.calio.calendar.external.google.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GoogleAccessTokenRefreshResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("access-token refresh 응답은 refresh_token 없이 Bearer access token을 반환한다")
    void givenValidRefreshResponse_whenParse_thenDoesNotRequireRefreshToken() throws Exception {
        // when
        GoogleAccessTokenRefreshResponse response = GoogleAccessTokenRefreshResponse.fromJson(
                """
                        {
                          "access_token": "new-access-token",
                          "expires_in": 3600,
                          "token_type": "Bearer"
                        }
                        """,
                objectMapper
        );

        // then
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    @DisplayName("access-token refresh 응답이 유효하지 않으면 reconnect required로 분류한다")
    void givenInvalidRefreshResponse_whenParse_thenRequiresReconnect() {
        // when, then
        assertThatThrownBy(() -> GoogleAccessTokenRefreshResponse.fromJson(
                """
                        {
                          "expires_in": 3600,
                          "token_type": "Bearer"
                        }
                        """,
                objectMapper
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED));
    }

    @Test
    @DisplayName("access-token refresh 응답은 Bearer token_type을 필수로 요구한다")
    void givenMissingTokenType_whenParse_thenRequiresReconnect() {
        // when, then
        assertThatThrownBy(() -> GoogleAccessTokenRefreshResponse.fromJson(
                """
                        {
                          "access_token": "new-access-token",
                          "expires_in": 3600
                        }
                        """,
                objectMapper
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED));
    }
}
