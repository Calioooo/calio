package com.calio.calendar.external.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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

    private GoogleOAuthProperties properties() {
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setTokenUrl("https://oauth.example.test/token");
        properties.setUserInfoUrl("https://oauth.example.test/userinfo");
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRedirectUri("https://example.com/oauth/callback");
        return properties;
    }
}
