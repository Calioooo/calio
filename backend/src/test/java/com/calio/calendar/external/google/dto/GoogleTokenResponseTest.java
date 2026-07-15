package com.calio.calendar.external.google.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GoogleTokenResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Google token response는 access token, refresh token, 양수 expires_in을 요구한다")
    void givenValidTokenResponse_whenParse_thenReturnsRequiredValues() throws Exception {
        // given
        String json = """
                {
                  "access_token": "access-token",
                  "refresh_token": "refresh-token",
                  "expires_in": 3600,
                  "token_type": "Bearer"
                }
                """;

        // when
        GoogleTokenResponse response = GoogleTokenResponse.fromJson(json, objectMapper);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    @DisplayName("Google token response의 token_type이 Bearer가 아니면 invalid response로 거부한다")
    void givenUnsupportedTokenType_whenParse_thenThrowsInvalidTokenResponse() {
        // given
        String json = """
                {
                  "access_token": "access-token",
                  "refresh_token": "refresh-token",
                  "expires_in": 3600,
                  "token_type": "mac"
                }
                """;

        // when, then
        assertThatThrownBy(() -> GoogleTokenResponse.fromJson(json, objectMapper))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_TOKEN_RESPONSE_INVALID));
    }
}
