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
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

class GoogleCalendarEventsClientTest {

    @Test
    @DisplayName("FULL SYNC 요청은 공통 query만 보내고 syncToken과 range parameter를 보내지 않는다")
    void givenFullMode_whenListEvents_thenUsesFullQueryContract() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(requestTo(allOf(
                        containsString("singleEvents=false"),
                        containsString("showDeleted=true"),
                        containsString("maxResults=2500"),
                        containsString("fields="),
                        containsString("originalStartTime"),
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
                "current-token",
                GoogleCalendarSyncMode.FULL,
                null,
                null
        );

        // then
        assertThat(page.nextSyncToken()).isEqualTo("next-token");
        server.verify();
    }

    @Test
    @DisplayName("Events API 응답의 일정 형식이 잘못되면 invalid response 예외를 반환한다")
    void givenMalformedEventResponse_whenListEvents_thenReturnsInvalidResponse() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(requestTo(containsString("singleEvents=false")))
                .andRespond(withSuccess(
                        """
                                {
                                  "items": [
                                    {
                                      "id": "event-1",
                                      "status": "confirmed"
                                    }
                                  ],
                                  "nextSyncToken": "next-token"
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        // when, then
        assertThatThrownBy(() -> client.listEvents(
                "current-token",
                GoogleCalendarSyncMode.FULL,
                null,
                null
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
        server.verify();
    }

    @Test
    @DisplayName("Events API 401은 token을 갱신하지 않고 401예외를 반환한다")
    void givenUnauthorizedResponse_whenListEvents_thenPropagatesUnauthorized() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(requestTo(containsString("syncToken=cursor")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer current-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // when, then
        assertThatThrownBy(() -> client.listEvents(
                "current-token",
                GoogleCalendarSyncMode.INCREMENTAL,
                "cursor",
                null
        )).isInstanceOf(GoogleCalendarUnauthorizedException.class);
        server.verify();
    }

    @Test
    @DisplayName("Events API 410은 원본 HTTP 실패를 보존한 sync token 만료 예외로 반환한다")
    void givenGoneResponse_whenListEvents_thenReturnsSyncTokenExpired() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(requestTo(containsString("syncToken=expired-cursor")))
                .andRespond(withStatus(HttpStatus.GONE));

        // when, then
        assertThatThrownBy(() -> client.listEvents(
                "current-token",
                GoogleCalendarSyncMode.INCREMENTAL,
                "expired-cursor",
                null
        )).isInstanceOfSatisfying(
                GoogleCalendarSyncTokenExpiredException.class,
                exception -> assertThat(exception.getCause())
                        .isInstanceOf(RestClientResponseException.class)
        );
        server.verify();
    }

    @Test
    @DisplayName("INCREMENTAL 요청에 cursor가 없으면 외부 호출 없이 invalid response 예외를 반환한다")
    void givenIncrementalModeWithoutCursor_whenListEvents_thenRejectsClosedQueryContract() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        GoogleCalendarEventsClient client = client(restClientBuilder);

        // when, then
        assertThatThrownBy(() -> client.listEvents(
                "current-token",
                GoogleCalendarSyncMode.INCREMENTAL,
                null,
                null
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
    }

    @Test
    @DisplayName("calendar scope 부족 403은 reconnect required 예외를 반환한다")
    void givenInsufficientPermissions_whenListEvents_thenRequiresReconnect() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
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
                "current-token",
                GoogleCalendarSyncMode.FULL,
                null,
                null
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED));
        server.verify();
    }

    @Test
    @DisplayName("일시적인 usage-limit 403은 sync failed 예외를 반환한다")
    void givenRateLimitFailure_whenListEvents_thenReturnsSyncFailed() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
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
                "current-token",
                GoogleCalendarSyncMode.FULL,
                null,
                null
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("parent 단건 조회는 opaque id 전체를 한 path segment로 encode한다")
    void givenOpaqueExternalId_whenGetEvent_thenEncodesWholeIdAndDecodesEvent() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(request -> assertThat(request.getURI().getRawPath())
                        .endsWith("/master%2Fsegment%3Fopaque"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer current-token"))
                .andRespond(withSuccess(
                        """
                                {
                                  "id": "master/segment?opaque",
                                  "status": "confirmed",
                                  "recurrence": ["RRULE:FREQ=DAILY"],
                                  "start": {
                                    "dateTime": "2026-07-01T09:00:00+09:00",
                                    "timeZone": "Asia/Seoul"
                                  },
                                  "end": {
                                    "dateTime": "2026-07-01T10:00:00+09:00",
                                    "timeZone": "Asia/Seoul"
                                  }
                                }
                                """,
                        MediaType.APPLICATION_JSON
                ));

        // when
        var result = client.getEvent(
                "current-token",
                "master/segment?opaque"
        );

        // then
        assertThat(result).map(event -> event.id()).contains("master/segment?opaque");
        server.verify();
    }

    @Test
    @DisplayName("parent 단건 조회 404는 not-found 예외를 반환한다")
    void givenMissingParent_whenGetEvent_thenReturnsNotFoundOutcome() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(requestTo(containsString("/events/missing-parent")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // when
        var result = client.getEvent(
                "current-token",
                "missing-parent"
        );

        // then
        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("parent 단건 조회 401은 token을 refresh 하지 않고 unauthorized 예외를 반환한다")
    void givenUnauthorizedParentLookup_whenGetEvent_thenPropagatesUnauthorized() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(requestTo(containsString("/events/master-id")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // when, then
        assertThatThrownBy(() -> client.getEvent("current-token", "master-id"))
                .isInstanceOf(GoogleCalendarUnauthorizedException.class);
        server.verify();
    }

    @Test
    @DisplayName("parent 단건 조회의 올바르지 않은 권한 조회 요청은 reconnect 예외를 반환한다")
    void givenInsufficientScopeParentLookup_whenGetEvent_thenRequiresReconnect() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(requestTo(containsString("/events/master-id")))
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
        assertThatThrownBy(() -> client.getEvent("current-token", "master-id"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED));
        server.verify();
    }

    @Test
    @DisplayName("parent 단건 조회의 rate-limit 응답은 sync failed 예외를 반환한다 ")
    void givenRateLimitParentLookup_whenGetEvent_thenReturnsSyncFailed() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(requestTo(containsString("/events/master-id")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // when, then
        assertThatThrownBy(() -> client.getEvent("current-token", "master-id"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("parent 단건 조회의 network failure는 sync failed 예외를 반환한다")
    void givenNetworkFailureParentLookup_whenGetEvent_thenReturnsSyncFailed() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(requestTo(containsString("/events/master-id")))
                .andRespond(request -> {
                    throw new IOException("network failure");
                });

        // when, then
        assertThatThrownBy(() -> client.getEvent("current-token", "master-id"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("parent 단건 조회 5xx는 sync failed 예외를 반환한다")
    void givenServerFailureParentLookup_whenGetEvent_thenReturnsSyncFailed() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(requestTo(containsString("/events/master-id")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // when, then
        assertThatThrownBy(() -> client.getEvent("current-token", "master-id"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("parent 단건 조회의 잘못된 body는 invalid response 예외로 반환한다")
    void givenMalformedParentBody_whenGetEvent_thenReturnsInvalidResponse() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(requestTo(containsString("/events/master-id")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when, then
        assertThatThrownBy(() -> client.getEvent("current-token", "master-id"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
        server.verify();
    }

    private GoogleCalendarEventsClient client(RestClient.Builder restClientBuilder) {
        GoogleOAuthProperties properties = new GoogleOAuthProperties();
        properties.setCalendarEventsUrl("https://calendar.example.test/events");
        return new GoogleCalendarEventsClient(
                properties,
                new ObjectMapper(),
                restClientBuilder.build()
        );
    }
}
