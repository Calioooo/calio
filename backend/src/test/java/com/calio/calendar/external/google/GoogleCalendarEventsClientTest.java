package com.calio.calendar.external.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventWriteRequest;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.sync.GoogleCalendarSyncMode;
import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
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
    @DisplayName("recurring recurrence-event CREATE는 RRULE과 sendUpdates=none을 보낸다")
    void recurrenceCreateSendsRulesWithoutOverrides() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleCalendarEventsClient client = client(builder);
        server.expect(requestTo(containsString("sendUpdates=none")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.id", is("provider-id")))
                .andExpect(jsonPath("$.recurrence[0]", is("RRULE:FREQ=DAILY")))
                .andExpect(jsonPath("$.originalStartTime").doesNotExist())
                .andRespond(withSuccess(eventResponse("provider-id"), MediaType.APPLICATION_JSON));

        client.post("token", GoogleCalendarEventWriteRequest.forRecurrenceCreate(
                new GoogleRecurrenceJobPayload(
                        "daily", null, Instant.parse("2026-09-04T00:00:00Z"),
                        Instant.parse("2026-09-04T01:00:00Z"), false, "UTC",
                        List.of("RRULE:FREQ=DAILY")),
                "provider-id"
        ));

        server.verify();
    }

    @Test
    @DisplayName("PATCH는 null write request를 invalid request로 거부한다")
    void givenNullWriteRequest_whenPatch_thenRejectsInvalidRequest() {
        // given
        GoogleCalendarEventsClient client = client(RestClient.builder());

        // when, then
        assertThatThrownBy(() -> client.patch("token", "event-1", "etag-1", null))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID));
    }

    @Test
    @DisplayName("exact occurrence resolve는 Google instances endpoint와 immutable originalStart를 사용한다")
    void resolvesExactRecurrenceOccurrenceByOriginalStart() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleCalendarEventsClient client = client(builder);
        server.expect(requestTo(allOf(containsString("/events/recurrence-event-1/instances"),
                        containsString("originalStart=2026-09-04T00:00:00Z"))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"items":[{"id":"occurrence-1","status":"confirmed","etag":"etag-i",
                        "recurringEventId":"recurrence-event-1",
                        "originalStartTime":{"dateTime":"2026-09-04T00:00:00Z","timeZone":"UTC"},
                        "start":{"dateTime":"2026-09-04T02:00:00Z","timeZone":"UTC"},
                        "end":{"dateTime":"2026-09-04T03:00:00Z","timeZone":"UTC"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getRecurrenceOccurrence("token", "recurrence-event-1",
                Instant.parse("2026-09-04T00:00:00Z"))).isPresent();
        server.verify();
    }

    @Test
    @DisplayName("override PATCH는 full final schedule을 보내고 RRULE은 보내지 않는다")
    void overridePatchSendsFullSnapshotWithoutRecurrenceRules() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleCalendarEventsClient client = client(builder);
        server.expect(requestTo(containsString("sendUpdates=none")))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.IF_MATCH, "etag-i"))
                .andExpect(jsonPath("$.start.dateTime", is("2026-09-04T02:00:00Z")))
                .andExpect(jsonPath("$.recurrence").doesNotExist())
                .andRespond(withSuccess(eventResponse("occurrence-1"), MediaType.APPLICATION_JSON));

        client.patch("token", "occurrence-1", "etag-i",
                GoogleCalendarEventWriteRequest.forOverrideUpdate(new GoogleRecurrenceOverrideJobPayload("moved", null,
                        Instant.parse("2026-09-04T02:00:00Z"),
                        Instant.parse("2026-09-04T03:00:00Z"), false, "UTC")));
        server.verify();
    }

    @Test
    @DisplayName("all-day override 전환은 timed field를 상속하지 않고 exclusive date snapshot을 보낸다")
    void allDayOverridePatchUsesCanonicalDateSnapshot() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleCalendarEventsClient client = client(builder);
        server.expect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.start.date", is("2026-09-04")))
                .andExpect(jsonPath("$.end.date", is("2026-09-06")))
                .andExpect(jsonPath("$.start.dateTime").doesNotExist())
                .andRespond(withSuccess(eventResponse("occurrence-1"), MediaType.APPLICATION_JSON));

        client.patch("token", "occurrence-1", "etag-i",
                GoogleCalendarEventWriteRequest.forOverrideUpdate(new GoogleRecurrenceOverrideJobPayload("offsite", null,
                        Instant.parse("2026-09-04T00:00:00Z"),
                        Instant.parse("2026-09-06T00:00:00Z"), true, null)));

        server.verify();
    }

    @Test
    @DisplayName("CREATE 재시도에서 동일한 Google Event ID가 이미 존재하면 기존 이벤트를 조회해 성공 처리한다")
    void givenExistingDeterministicEventId_whenPost() {
        // given
        String providerIdentity = "c10000000000000014000000000000028";
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.id", is(providerIdentity)))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(requestTo(containsString("/events/" + providerIdentity)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(eventResponse(providerIdentity), MediaType.APPLICATION_JSON));

        // when
        GoogleCalendarEventResponse response = client.post(
                "current-token",
                GoogleCalendarEventWriteRequest.forEventCreate(payload(), providerIdentity)
        );

        // then
        assertThat(response.id()).isEqualTo(providerIdentity);
        server.verify();
    }

    @Test
    @DisplayName("PATCH는 읽은 provider ETag를 If-Match precondition으로 보낸다")
    void givenExpectedProviderEtag_whenPatch_thenSendsConditionalRequest() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(method(HttpMethod.PATCH))
                .andExpect(header(HttpHeaders.IF_MATCH, "etag-1"))
                .andRespond(withSuccess(eventResponse("event-1"), MediaType.APPLICATION_JSON));

        // when
        client.patch(
                "current-token",
                "event-1",
                "etag-1",
                GoogleCalendarEventWriteRequest.forEventUpdate(payload())
        );

        // then
        server.verify();
    }

    @Test
    @DisplayName("PATCH의 412 precondition 실패는 provider conflict 예외로 구분한다")
    void givenPreconditionFailed_whenPatch_thenReturnsProviderConflict() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        // when, then
        assertThatThrownBy(() -> client.patch(
                "current-token",
                "event-1",
                "etag-1",
                GoogleCalendarEventWriteRequest.forEventUpdate(payload())
        )).isInstanceOf(GoogleCalendarEventVersionConflictException.class);
        server.verify();
    }

    @Test
    @DisplayName("CREATE 재시도의 409 뒤 기존 Google Event를 찾지 못하면 sync failed를 반환한다")
    void givenMissingEventAfterCreateConflict_whenPost_thenReturnsSyncFailed() {
        // given
        String providerIdentity = "c10000000000000014000000000000028";
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // when, then
        assertThatThrownBy(() -> client.post(
                "current-token", GoogleCalendarEventWriteRequest.forEventCreate(payload(), providerIdentity)))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_SYNC_FAILED));
        server.verify();
    }

    @Test
    @DisplayName("CREATE 재시도는 conflict 뒤 조회한 기존 Google Event를 반환한다")
    void givenExistingEventAfterCreateConflict_whenPost() {
        // given
        String providerIdentity = "c10000000000000014000000000000028";
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(method(HttpMethod.GET))
                .andRespond(withSuccess(eventResponse("different-event-id"), MediaType.APPLICATION_JSON));

        // when
        GoogleCalendarEventResponse response = client.post(
                "current-token", GoogleCalendarEventWriteRequest.forEventCreate(payload(), providerIdentity));

        // then
        assertThat(response.id()).isEqualTo("different-event-id");
        server.verify();
    }

    @Test
    @DisplayName("DELETE는 읽은 provider ETag를 If-Match precondition으로 보낸다")
    void givenExpectedProviderEtag_whenDelete_thenSendsConditionalRequest() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(method(HttpMethod.DELETE))
                .andExpect(header(HttpHeaders.IF_MATCH, "etag-1"))
                .andRespond(withSuccess());

        // when
        client.delete("current-token", "event-1", "etag-1");

        // then
        server.verify();
    }

    @Test
    @DisplayName("DELETE의 412 precondition 실패는 provider conflict 예외로 구분한다")
    void givenPreconditionFailed_whenDelete_thenReturnsProviderConflict() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.PRECONDITION_FAILED));

        // when, then
        assertThatThrownBy(() -> client.delete("current-token", "event-1", "etag-1"))
                .isInstanceOf(GoogleCalendarEventVersionConflictException.class);
        server.verify();
    }

    @Test
    @DisplayName("DELETE의 410 응답은 이미 삭제된 일정으로 처리한다")
    void givenGoneResponse_whenDelete_thenTreatsDeletionAsComplete() {
        // given
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GoogleCalendarEventsClient client = client(restClientBuilder);
        server.expect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.GONE));

        // when
        boolean deleted = client.delete("current-token", "event-1", "etag-1");

        // then
        assertThat(deleted).isFalse();
        server.verify();
    }

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
    @DisplayName("INCREMENTAL 요청에 cursor가 없으면 외부 호출 없이 invalid request 예외를 반환한다")
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
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID));
    }

    @Test
    @DisplayName("단건 조회에 external event ID가 없으면 외부 호출 없이 invalid request 예외를 반환한다")
    void givenMissingExternalEventId_whenGetEvent_thenRejectsRequestContract() {
        // given
        GoogleCalendarEventsClient client = client(RestClient.builder());

        // when, then
        assertThatThrownBy(() -> client.getEvent("current-token", " "))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID));
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
                        .endsWith("/recurrence-event%2Fsegment%3Fopaque"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer current-token"))
                .andRespond(withSuccess(
                        """
                                {
                                  "id": "recurrence-event/segment?opaque",
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
                "recurrence-event/segment?opaque"
        );

        // then
        assertThat(result).map(event -> event.id()).contains("recurrence-event/segment?opaque");
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
        server.expect(requestTo(containsString("/events/recurrence-event-id")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        // when, then
        assertThatThrownBy(() -> client.getEvent("current-token", "recurrence-event-id"))
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
        server.expect(requestTo(containsString("/events/recurrence-event-id")))
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
        assertThatThrownBy(() -> client.getEvent("current-token", "recurrence-event-id"))
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
        server.expect(requestTo(containsString("/events/recurrence-event-id")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        // when, then
        assertThatThrownBy(() -> client.getEvent("current-token", "recurrence-event-id"))
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
        server.expect(requestTo(containsString("/events/recurrence-event-id")))
                .andRespond(request -> {
                    throw new IOException("network failure");
                });

        // when, then
        assertThatThrownBy(() -> client.getEvent("current-token", "recurrence-event-id"))
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
        server.expect(requestTo(containsString("/events/recurrence-event-id")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // when, then
        assertThatThrownBy(() -> client.getEvent("current-token", "recurrence-event-id"))
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
        server.expect(requestTo(containsString("/events/recurrence-event-id")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when, then
        assertThatThrownBy(() -> client.getEvent("current-token", "recurrence-event-id"))
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

    private String eventResponse(String eventId) {
        return """
                {
                  "id": "%s",
                  "status": "confirmed",
                  "etag": "etag",
                  "start": {"dateTime": "2026-09-04T00:00:00Z", "timeZone": "UTC"},
                  "end": {"dateTime": "2026-09-04T01:00:00Z", "timeZone": "UTC"}
                }
                """.formatted(eventId);
    }

    private GoogleEventJobPayload payload() {
        return new GoogleEventJobPayload(
                "title", null,
                Instant.parse("2026-09-04T00:00:00Z"),
                Instant.parse("2026-09-04T01:00:00Z"),
                false,
                "UTC"
        );
    }
}
