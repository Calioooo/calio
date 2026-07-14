package com.calio.calendar.integration.googlecalendar.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.integration.googlecalendar.client.GoogleOAuthClient;
import com.calio.calendar.integration.googlecalendar.client.GoogleTokenResponse;
import com.calio.calendar.integration.googlecalendar.client.GoogleUserInfoResponse;
import com.calio.calendar.integration.googlecalendar.config.GoogleOAuthProperties;
import com.calio.calendar.integration.googlecalendar.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-google-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "external.google.oauth.client-id=client-id",
        "external.google.oauth.client-secret=client-secret",
        "external.google.oauth.redirect-uri=https://app.test/oauth/callback",
        "security.token-encryption.google-refresh-token-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import(AuthenticatedAccountMockMvcTestConfig.class)
class GoogleCalendarIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GoogleCalendarIntegrationRepository integrationRepository;

    @BeforeEach
    void setUp() {
        integrationRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/integrations/google-calendar는 연결 metadata만 응답하고 token은 노출하지 않는다")
    void givenAuthorizationCode_whenConnect_thenReturnsConnectionMetadataOnly() throws Exception {
        // when
        mockMvc.perform(post("/api/integrations/google-calendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorizationCode": "authorization-code"
                                }
                                """))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.googleEmail").value("user@example.com"))
                .andExpect(jsonPath("$.googleSubject").value("google-subject"))
                .andExpect(jsonPath("$.connectedAt").value("2026-07-14T00:00:00Z"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.*", hasSize(4)));
    }

    @Test
    @DisplayName("authorizationCode가 공백이면 VALIDATION_FAILED를 반환한다")
    void givenBlankAuthorizationCode_whenConnect_thenReturnsValidationFailed() throws Exception {
        // when
        mockMvc.perform(post("/api/integrations/google-calendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorizationCode": " "
                                }
                                """))
                // then
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("연결 row가 없으면 GET은 disconnected shape를 반환한다")
    void givenNoIntegration_whenGetStatus_thenReturnsDisconnectedShape() throws Exception {
        // when
        mockMvc.perform(get("/api/integrations/google-calendar"))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.googleEmail").value(nullValue()))
                .andExpect(jsonPath("$.googleSubject").value(nullValue()))
                .andExpect(jsonPath("$.connectedAt").value(nullValue()));
    }

    @Test
    @DisplayName("DELETE는 Integration row가 없어도 204 No Content를 반환한다")
    void givenNoIntegration_whenDisconnect_thenReturnsNoContent() throws Exception {
        // when
        mockMvc.perform(delete("/api/integrations/google-calendar"))
                // then
                .andExpect(status().isNoContent());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Primary
        GoogleOAuthClient fakeGoogleOAuthClient() {
            return new GoogleOAuthClient(new GoogleOAuthProperties(), new ObjectMapper()) {
                @Override
                public GoogleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
                    return new GoogleTokenResponse("access-token", "refresh-token", 3600);
                }

                @Override
                public GoogleUserInfoResponse fetchUserInfo(String accessToken) {
                    return new GoogleUserInfoResponse("google-subject", "user@example.com");
                }
            };
        }
    }
}
