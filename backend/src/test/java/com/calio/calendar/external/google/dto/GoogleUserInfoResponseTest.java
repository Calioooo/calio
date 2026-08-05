package com.calio.calendar.external.google.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GoogleUserInfoResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Google UserInfo response는 sub와 email을 account identity로 반환한다")
    void givenValidUserInfoResponse_whenParse_thenReturnsIdentity() throws Exception {
        // given
        String json = """
                {
                  "sub": "google-subject",
                  "email": "user@example.com",
                  "email_verified": true
                }
                """;

        // when
        GoogleUserInfoResponse response = objectMapper.readValue(
                json,
                GoogleUserInfoResponse.class
        );

        // then
        assertThat(response.subject()).isEqualTo("google-subject");
        assertThat(response.email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("Google UserInfo response의 email_verified가 false이면 invalid user info로 거부한다")
    void givenUnverifiedEmail_whenParse_thenThrowsInvalidUserInfo() {
        // given
        String json = """
                {
                  "sub": "google-subject",
                  "email": "user@example.com",
                  "email_verified": false
                }
                """;

        // when, then
        assertThatThrownBy(() -> objectMapper.readValue(json, GoogleUserInfoResponse.class))
                .satisfies(exception ->
                        assertErrorCode(exception, ErrorCode.GOOGLE_USER_INFO_INVALID));
    }

    private void assertErrorCode(Throwable throwable, ErrorCode errorCode) {
        Throwable cause = throwable;
        while (cause != null && !(cause instanceof CalioException)) {
            cause = cause.getCause();
        }
        assertThat(cause).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
