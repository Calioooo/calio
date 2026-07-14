package com.calio.calendar.integration.googlecalendar.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.googlecalendar.config.GoogleOAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GoogleOAuthClientTest {

    private MockRestServiceServer server;
    private GoogleOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleOAuthClient(properties(), new ObjectMapper(), builder.build());
    }

    @Test
    @DisplayName("authorization code를 Google token endpoint에 form 요청으로 교환한다")
    void givenAuthorizationCode_whenExchangeToken_thenReturnsValidatedTokenResponse() {
        // given
        server.expect(once(), requestTo("https://oauth.test/token"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token",
                          "refresh_token": "refresh-token",
                          "expires_in": 3600,
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        // when
        GoogleTokenResponse response = client.exchangeAuthorizationCode("authorization-code");

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.expiresInSeconds()).isEqualTo(3600);
        server.verify();
    }

    @Test
    @DisplayName("refresh_token 없는 token 응답은 GOOGLE_OAUTH_INVALID_TOKEN_RESPONSE로 거부한다")
    void givenMissingRefreshToken_whenExchangeToken_thenThrowsInvalidTokenResponse() {
        // given
        server.expect(once(), requestTo("https://oauth.test/token"))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token",
                          "expires_in": 3600,
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        // when then
        assertThatThrownBy(() -> client.exchangeAuthorizationCode("authorization-code"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_OAUTH_INVALID_TOKEN_RESPONSE));
        server.verify();
    }

    @Test
    @DisplayName("Google token endpoint 오류는 GOOGLE_OAUTH_TOKEN_EXCHANGE_FAILED로 변환한다")
    void givenGoogleTokenError_whenExchangeToken_thenThrowsTokenExchangeFailed() {
        // given
        server.expect(once(), requestTo("https://oauth.test/token"))
                .andRespond(withBadRequest());

        // when then
        assertThatThrownBy(() -> client.exchangeAuthorizationCode("authorization-code"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_OAUTH_TOKEN_EXCHANGE_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("access token으로 Google userinfo를 조회하고 sub/email만 반환한다")
    void givenAccessToken_whenFetchUserInfo_thenReturnsValidatedUserInfo() {
        // given
        server.expect(once(), requestTo("https://oauth.test/userinfo"))
                .andExpect(method(GET))
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
    @DisplayName("email_verified=false userinfo 응답은 GOOGLE_OAUTH_INVALID_USERINFO_RESPONSE로 거부한다")
    void givenUnverifiedEmail_whenFetchUserInfo_thenThrowsInvalidUserInfoResponse() {
        // given
        server.expect(once(), requestTo("https://oauth.test/userinfo"))
                .andRespond(withSuccess("""
                        {
                          "sub": "google-subject",
                          "email": "user@example.com",
                          "email_verified": false
                        }
                        """, MediaType.APPLICATION_JSON));

        // when then
        assertThatThrownBy(() -> client.fetchUserInfo("access-token"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_OAUTH_INVALID_USERINFO_RESPONSE));
        server.verify();
    }

    private GoogleOAuthProperties properties() {
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setTokenUrl("https://oauth.test/token");
        properties.setUserInfoUrl("https://oauth.test/userinfo");
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("https://app.test/oauth/callback");
        return properties;
    }
}
