package com.calio.calendar.external.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.integration.service.GoogleCalendarAccessTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GoogleCalendarEventsClientTest {

    @Test
    @DisplayName("FULL 요청은 공통 query만 보내고 syncToken과 range parameter를 보내지 않는다")
    void givenFullMode_whenListEvents_thenUsesFullQueryContract() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        FakeAccessTokenService tokenService = new FakeAccessTokenService();
        GoogleCalendarEventsClient client = client(tokenService, restClientBuilder);
        server.expect(requestTo(allOf(
                        containsString("singleEvents=false"),
                        containsString("showDeleted=true"),
                        containsString("maxResults=2500"),
                        containsString("fields="),
                        not(containsString("syncToken")),
                        not(containsString("timeMin")),
                        not(containsString("timeMax"))
                )))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer current-token"))
                .andRespond(withSuccess(
                        "{\"items\":[],\"nextSyncToken\":\"next-token\"}",
                        MediaType.APPLICATION_JSON
                ));

        // when
        GoogleCalendarEventPage page = client.listEvents(
                1L,
                GoogleCalendarSyncMode.FULL,
                null,
                null
        );

        // then
        assertThat(page.nextSyncToken()).isEqualTo("next-token");
        server.verify();
    }

    @Test
    @DisplayName("Events API 401은 강제 refresh 후 동일 요청을 한 번 재시도한다")
    void givenUnauthorizedResponse_whenListEvents_thenRefreshesAndRetriesOnce() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        FakeAccessTokenService tokenService = new FakeAccessTokenService();
        GoogleCalendarEventsClient client = client(tokenService, restClientBuilder);
        server.expect(requestTo(containsString("syncToken=cursor")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer current-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(containsString("syncToken=cursor")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer refreshed-token"))
                .andRespond(withSuccess(
                        "{\"items\":[],\"nextSyncToken\":\"next-token\"}",
                        MediaType.APPLICATION_JSON
                ));

        // when
        client.listEvents(1L, GoogleCalendarSyncMode.INCREMENTAL, "cursor", null);

        // then
        assertThat(tokenService.forceRefreshCount).isOne();
        server.verify();
    }

    @Test
    @DisplayName("INCREMENTAL 요청에 cursor가 없으면 외부 호출 없이 invalid response로 거부한다")
    void givenIncrementalModeWithoutCursor_whenListEvents_thenRejectsClosedQueryContract() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        FakeAccessTokenService tokenService = new FakeAccessTokenService();
        GoogleCalendarEventsClient client = client(tokenService, restClientBuilder);

        // when, then
        assertThatThrownBy(() -> client.listEvents(
                1L,
                GoogleCalendarSyncMode.INCREMENTAL,
                null,
                null
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
        assertThat(tokenService.getAccessTokenCount).isZero();
    }

    @Test
    @DisplayName("calendar scope 부족 403은 reconnect required로 분류한다")
    void givenInsufficientPermissions_whenListEvents_thenRequiresReconnect() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(
                new FakeAccessTokenService(),
                restClientBuilder
        );
        server.expect(requestTo(containsString("singleEvents=false")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "errors": [
                                      {"reason": "insufficientPermissions"}
                                    ]
                                  }
                                }
                                """));

        // when, then
        assertThatThrownBy(() -> client.listEvents(
                1L,
                GoogleCalendarSyncMode.FULL,
                null,
                null
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED));
        server.verify();
    }

    @Test
    @DisplayName("일시적인 usage-limit 403은 reconnect가 아닌 sync failed로 분류한다")
    void givenRateLimitFailure_whenListEvents_thenReturnsSyncFailed() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(
                new FakeAccessTokenService(),
                restClientBuilder
        );
        server.expect(requestTo(containsString("singleEvents=false")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "errors": [
                                      {"reason": "rateLimitExceeded"}
                                    ]
                                  }
                                }
                                """));

        // when, then
        assertThatThrownBy(() -> client.listEvents(
                1L,
                GoogleCalendarSyncMode.FULL,
                null,
                null
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED));
        server.verify();
    }

    private GoogleCalendarEventsClient client(
            FakeAccessTokenService tokenService,
            RestClient.Builder restClientBuilder
    ) {
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setCalendarEventsUrl("https://calendar.example.test/events");
        return new GoogleCalendarEventsClient(
                properties,
                tokenService,
                new ObjectMapper(),
                restClientBuilder.build()
        );
    }

    private static class FakeAccessTokenService extends GoogleCalendarAccessTokenService {

        private int getAccessTokenCount;
        private int forceRefreshCount;

        FakeAccessTokenService() {
            super(null, null, null, null);
        }

        @Override
        public String getAccessToken(Long integrationId) {
            getAccessTokenCount++;
            return "current-token";
        }

        @Override
        public String forceRefresh(Long integrationId) {
            forceRefreshCount++;
            return "refreshed-token";
        }
    }
}
