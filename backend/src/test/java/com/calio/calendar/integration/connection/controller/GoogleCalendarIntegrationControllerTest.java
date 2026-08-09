package com.calio.calendar.integration.connection.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.calio.calendar.external.google.GoogleOAuthClient;
import com.calio.calendar.external.google.GoogleOAuthProperties;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.external.google.dto.GoogleTokenResponse;
import com.calio.calendar.external.google.dto.GoogleUserInfoResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.sync.GoogleCalendarSyncMode;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobTrigger;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.sync.operation.repository.GoogleOperationJobRepository;
import com.calio.calendar.integration.sync.operation.GoogleOperationWorker;
import com.calio.calendar.security.AuthenticatedAccountMockMvcTestConfig;
import com.calio.calendar.security.WithAuthenticatedAccount;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-google-integration-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "external.google.oauth.client-id=test-client-id",
        "external.google.oauth.client-secret=test-client-secret",
        "external.google.oauth.redirect-uri=https://example.com/oauth/callback",
        "security.token-encryption.google-refresh-token-key=12345678901234567890123456789012"
})
@AutoConfigureMockMvc
@WithAuthenticatedAccount
@Import({
        AuthenticatedAccountMockMvcTestConfig.class,
        GoogleCalendarIntegrationControllerTest.GoogleOAuthClientMockConfig.class
})
class GoogleCalendarIntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FakeGoogleOAuthClient googleOAuthClient;

    @Autowired
    private FakeGoogleCalendarEventsClient googleCalendarEventsClient;

    @Autowired
    private GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository;

    @Autowired
    private GoogleOperationJobRepository googleOperationJobRepository;

    @MockitoBean
    private GoogleOperationWorker googleOperationWorker;

    @BeforeEach
    void setUp() {
        googleOperationJobRepository.deleteAll();
        googleCalendarIntegrationRepository.deleteAll();
        googleOAuthClient.reset();
        googleCalendarEventsClient.reset();
    }

    @Test
    @DisplayName("인증되지 않은 사용자는 Google Calendar integration API를 호출할 수 없다")
    void givenUnauthenticatedRequest_whenGetConnection_thenReturnsUnauthorized() throws Exception {
        // when, then
        mockMvc.perform(get("/api/integrations/google-calendar").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("구글 연동 데이터가 없으면 GET은 token 정보를 포함하지 않는 disconnected 상태를 반환한다")
    void givenNoIntegration_whenGetConnection_thenReturnsDisconnectedState() throws Exception {
        // when, then
        mockMvc.perform(get("/api/integrations/google-calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.googleEmail").value(nullValue()))
                .andExpect(jsonPath("$.googleSubject").value(nullValue()))
                .andExpect(jsonPath("$.connectedAt").value(nullValue()))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.accessTokenExpiresAt").doesNotExist());
    }

    @Test
    @DisplayName("POST는 authorizationCode로 Google 계정을 연결하고 정상 응답을 반환한다")
    void givenAuthorizationCode_whenConnect_thenReturnsConnectedState() throws Exception {
        // given
        googleOAuthClient.tokenResponse = new GoogleTokenResponse("access-token", "refresh-token", 3600);
        googleOAuthClient.userInfoResponse = new GoogleUserInfoResponse("google-subject", "user@example.com");

        // when, then
        mockMvc.perform(post("/api/integrations/google-calendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorizationCode": "auth-code"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.googleEmail").value("user@example.com"))
                .andExpect(jsonPath("$.googleSubject").value("google-subject"))
                .andExpect(jsonPath("$.connectedAt").isString())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.accessTokenExpiresAt").doesNotExist());
    }

    @Test
    @DisplayName("DELETE는 구글 연결 데이터가 없어도 204 No Content를 반환한다")
    void givenNoIntegration_whenDisconnect_thenReturnsNoContent() throws Exception {
        // when, then
        mockMvc.perform(delete("/api/integrations/google-calendar"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("연결되지 않은 Account가 sync를 요청하면 GOOGLE_CALENDAR_NOT_CONNECTED 409 예외를 반환한다")
    void givenNoIntegration_whenSync_thenReturnsNotConnected() throws Exception {
        // when, then
        mockMvc.perform(post("/api/integrations/google-calendar/sync"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("GOOGLE_CALENDAR_NOT_CONNECTED"));
        assertThat(googleCalendarEventsClient.listCount).isZero();
    }

    @Test
    @DisplayName("연결된 Account의 sync 요청은 실행 결과 없이 202 Accepted를 반환한다")
    void givenConnection_whenSync_thenReturnsAcceptedWithoutOperationMetadata() throws Exception {
        // given
        connectGoogleCalendar();

        // when, then
        mockMvc.perform(post("/api/integrations/google-calendar/sync"))
                .andExpect(status().isAccepted())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());

        GoogleCalendarIntegration integration = googleCalendarIntegrationRepository.findAll().getFirst();
        List<GoogleOperationJob> jobs = googleOperationJobRepository.findAll();
        assertThat(jobs).hasSize(1);
        GoogleOperationJob job = jobs.getFirst();
        assertThat(job.getOperationId()).isNotBlank();
        assertThat(job.getIntegrationId()).isEqualTo(integration.getId());
        assertThat(job.getAccountId()).isEqualTo(integration.getAccountId());
        assertThat(job.getAccountSequence()).isEqualTo(1L);
        assertThat(job.getKind()).isEqualTo(GoogleOperationJob.SYNC_KIND);
        assertThat(job.getTrigger()).isEqualTo(GoogleOperationJobTrigger.MANUAL);
        assertThat(job.getState()).isEqualTo(GoogleOperationJobState.PENDING);
        assertThat(job.getRunnableAt()).isNotNull();
        assertThat(job.getRetryCount()).isZero();
        assertThat(job.getOwnerToken()).isNull();
    }

    private void connectGoogleCalendar() throws Exception {
        googleOAuthClient.tokenResponse = new GoogleTokenResponse(
                "access-token",
                "refresh-token",
                3600
        );
        googleOAuthClient.userInfoResponse = new GoogleUserInfoResponse(
                "google-subject",
                "user@example.com"
        );
        mockMvc.perform(post("/api/integrations/google-calendar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorizationCode": "auth-code"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class GoogleOAuthClientMockConfig {

        @Bean
        @Primary
        FakeGoogleOAuthClient googleOAuthClientMock() {
            GoogleOAuthProperties properties = new GoogleOAuthProperties();
            properties.setTokenUrl("https://oauth2.googleapis.com/token");
            properties.setUserInfoUrl("https://www.googleapis.com/oauth2/v3/userinfo");
            properties.setClientId("test-client-id");
            properties.setClientSecret("test-client-secret");
            properties.setRedirectUri("https://example.com/oauth/callback");
            return new FakeGoogleOAuthClient(properties);
        }

        @Bean
        @Primary
        FakeGoogleCalendarEventsClient googleCalendarEventsClientMock(
                GoogleOAuthProperties properties,
                ObjectMapper objectMapper
        ) {
            return new FakeGoogleCalendarEventsClient(properties, objectMapper);
        }
    }

    static class FakeGoogleOAuthClient extends GoogleOAuthClient {

        private GoogleTokenResponse tokenResponse;
        private GoogleUserInfoResponse userInfoResponse;

        FakeGoogleOAuthClient(GoogleOAuthProperties properties) {
            super(properties, new ObjectMapper(), RestClient.builder().build());
        }

        @Override
        public GoogleTokenResponse exchangeAuthorizationCode(String authorizationCode) {
            return tokenResponse;
        }

        @Override
        public GoogleUserInfoResponse fetchUserInfo(String accessToken) {
            return userInfoResponse;
        }

        private void reset() {
            tokenResponse = null;
            userInfoResponse = null;
        }
    }

    static class FakeGoogleCalendarEventsClient extends GoogleCalendarEventsClient {

        private int listCount;
        private GoogleCalendarSyncMode lastMode;
        private final List<GoogleCalendarSyncMode> requestedModes = new ArrayList<>();
        private RuntimeException nextFailure;

        FakeGoogleCalendarEventsClient(
                GoogleOAuthProperties properties,
                ObjectMapper objectMapper
        ) {
            super(properties, objectMapper, RestClient.builder().build());
        }

        @Override
        public GoogleCalendarEventPage listEvents(
                String accessToken,
                GoogleCalendarSyncMode mode,
                String syncToken,
                String pageToken
        ) {
            listCount++;
            lastMode = mode;
            requestedModes.add(mode);
            if (nextFailure != null) {
                RuntimeException failure = nextFailure;
                nextFailure = null;
                throw failure;
            }
            return new GoogleCalendarEventPage(List.of(), null, "next-sync-token", "UTC");
        }

        private void reset() {
            listCount = 0;
            lastMode = null;
            requestedModes.clear();
            nextFailure = null;
        }
    }
}
