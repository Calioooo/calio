package com.calio.calendar.external.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleAccessTokenRefreshResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GoogleOAuthClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("GoogleOAuthClient는 authorization_code grant를 form encoding으로 token endpoint에 보낸다")
    void givenAuthorizationCode_whenExchangeToken_thenSendsFormEncodedGrantRequest() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(properties(), objectMapper, restClientBuilder.build());
        server.expect(requestTo("https://oauth.example.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(allOf(
                        containsString("code=auth-code"),
                        containsString("client_id=client-id"),
                        containsString("client_secret=client-secret"),
                        containsString("redirect_uri=https%3A%2F%2Fexample.com%2Foauth%2Fcallback"),
                        containsString("grant_type=authorization_code")
                )))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token",
                          "refresh_token": "refresh-token",
                          "expires_in": 3600,
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        GoogleTokenResponse response = client.exchangeAuthorizationCode("auth-code");

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        server.verify();
    }

    @Test
    @DisplayName("GoogleOAuthClient는 access token을 Bearer header로 UserInfo endpoint에 보낸다")
    void givenAccessToken_whenFetchUserInfo_thenSendsBearerAuthorizationHeader() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(properties(), objectMapper, restClientBuilder.build());
        server.expect(requestTo("https://oauth.example.test/userinfo"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-token"))
                .andRespond(withSuccess("""
                        {
                          "sub": "google-subject",
                          "email": "user@example.com",
                          "email_verified": true
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        GoogleUserInfoResponse response = client.fetchUserInfo("access-token");

        // then
        assertThat(response.subject()).isEqualTo("google-subject");
        assertThat(response.email()).isEqualTo("user@example.com");
        server.verify();
    }

    @Test
    @DisplayName("GoogleOAuthClient는 refresh token revoke를 form encoding으로 revoke endpoint에 보낸다")
    void givenRefreshToken_whenRevokeToken_thenSendsFormEncodedRevokeRequest() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(properties(), objectMapper, restClientBuilder.build());
        server.expect(requestTo("https://oauth.example.test/revoke"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("token=refresh-token")))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        // when
        boolean revoked = client.revokeToken("refresh-token");

        // then
        assertThat(revoked).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("GoogleOAuthClient는 revoke 응답의 invalid_token error를 삭제 허용 상태로 반환한다")
    void givenInvalidTokenRevokeResponse_whenRevokeToken_thenReturnsFalse() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(properties(), objectMapper, restClientBuilder.build());
        server.expect(requestTo("https://oauth.example.test/revoke"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": "invalid_token"
                                }
                                """));

        // when
        boolean revoked = client.revokeToken("refresh-token");

        // then
        assertThat(revoked).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("GoogleOAuthClient는 invalid_token 외 revoke 실패를 GOOGLE_TOKEN_REVOKE_FAILED로 반환한다")
    void givenUnexpectedRevokeFailure_whenRevokeToken_thenThrowsRevokeFailed() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(properties(), objectMapper, restClientBuilder.build());
        server.expect(requestTo("https://oauth.example.test/revoke"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        // when, then
        assertThatThrownBy(() -> client.revokeToken("refresh-token"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GOOGLE_TOKEN_REVOKE_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("access-token refresh는 refresh_token grant를 보내고 새 refresh token을 요구하지 않는다")
    void givenRefreshToken_whenRefreshAccessToken_thenUsesRefreshGrantContract() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(
                properties(),
                objectMapper,
                restClientBuilder.build()
        );
        server.expect(requestTo("https://oauth.example.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(allOf(
                        containsString("refresh_token=refresh-token"),
                        containsString("client_id=client-id"),
                        containsString("client_secret=client-secret"),
                        containsString("grant_type=refresh_token")
                )))
                .andRespond(withSuccess("""
                        {
                          "access_token": "new-access-token",
                          "expires_in": 3600,
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        GoogleAccessTokenRefreshResponse response =
                client.refreshAccessToken("refresh-token");

        // then
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.expiresIn()).isEqualTo(3600);
        server.verify();
    }

    @ParameterizedTest(name = "invalid_grant가 아닌 HTTP {0} refresh 실패는 재시도 대상으로 유지한다")
    @DisplayName("invalid_grant가 확인되지 않은 refresh 실패는 연결을 끊지 않는다")
    @EnumSource(
            value = HttpStatus.class,
            names = {"BAD_REQUEST", "UNAUTHORIZED"}
    )
    void givenUnconfirmedRefreshFailure_whenRefreshAccessToken_thenRemainsRetryable(
            HttpStatus status
    ) {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(
                properties(),
                objectMapper,
                restClientBuilder.build()
        );
        server.expect(requestTo("https://oauth.example.test/token"))
                .andRespond(withStatus(status));

        // when, then
        assertThatThrownBy(() -> client.refreshAccessToken("refresh-token"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("refresh 응답이 invalid_grant이면 confirmed credential revocation으로 분류한다")
    void givenInvalidGrant_whenRefreshAccessToken_thenSignalsConfirmedRevocation() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(
                properties(),
                objectMapper,
                restClientBuilder.build()
        );
        server.expect(requestTo("https://oauth.example.test/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"invalid_grant\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        // when, then
        assertThatThrownBy(() -> client.refreshAccessToken("refresh-token"))
                .isInstanceOf(GoogleOAuthInvalidGrantException.class);
        server.verify();
    }

    @Test
    @DisplayName("token 응답 역직렬화 실패는 GOOGLE_TOKEN_RESPONSE_INVALID로 매핑한다")
    void givenMalformedTokenResponse_whenExchangeToken_thenReturnsInvalidTokenResponse() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(
                properties(),
                objectMapper,
                restClientBuilder.build()
        );
        server.expect(requestTo("https://oauth.example.test/token"))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

        // when, then
        assertThatThrownBy(() -> client.exchangeAuthorizationCode("auth-code"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_TOKEN_RESPONSE_INVALID));
        server.verify();
    }

    @Test
    @DisplayName("UserInfo 생성자 검증 실패는 GOOGLE_USER_INFO_INVALID로 매핑한다")
    void givenUnverifiedUserInfo_whenFetchUserInfo_thenReturnsInvalidUserInfo() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(
                properties(),
                objectMapper,
                restClientBuilder.build()
        );
        server.expect(requestTo("https://oauth.example.test/userinfo"))
                .andRespond(withSuccess("""
                        {
                          "sub": "google-subject",
                          "email": "user@example.com",
                          "email_verified": false
                        }
                        """, MediaType.APPLICATION_JSON));

        // when, then
        assertThatThrownBy(() -> client.fetchUserInfo("access-token"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_USER_INFO_INVALID));
        server.verify();
    }

    @Test
    @DisplayName("refresh 응답 생성자 검증 실패는 GOOGLE_CALENDAR_RECONNECT_REQUIRED로 매핑한다")
    void givenInvalidRefreshResponse_whenRefreshAccessToken_thenRequiresReconnect() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleOAuthClient client = new GoogleOAuthClient(
                properties(),
                objectMapper,
                restClientBuilder.build()
        );
        server.expect(requestTo("https://oauth.example.test/token"))
                .andRespond(withSuccess("""
                        {
                          "access_token": "new-access-token",
                          "expires_in": 3600
                        }
                        """, MediaType.APPLICATION_JSON));

        // when, then
        assertThatThrownBy(() -> client.refreshAccessToken("refresh-token"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED));
        server.verify();
    }

    private GoogleOAuthProperties properties() {
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setTokenUrl("https://oauth.example.test/token");
        properties.setUserInfoUrl("https://oauth.example.test/userinfo");
        properties.setRevokeUrl("https://oauth.example.test/revoke");
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("https://example.com/oauth/callback");
        return properties;
    }
}
