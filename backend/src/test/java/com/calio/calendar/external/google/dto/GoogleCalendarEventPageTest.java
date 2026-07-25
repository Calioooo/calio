package com.calio.calendar.external.google.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GoogleCalendarEventPageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Google page는 일반, recurring master, exception, cancelled item을 구분한다")
    void givenProviderItems_whenParse_thenClassifiesItemsWithoutExpandingRecurrence()
            throws Exception {
        // when
        GoogleCalendarEventPage page = GoogleCalendarEventPage.fromJson(
                """
                        {
                          "nextSyncToken": "next-token",
                          "timeZone": "Asia/Seoul",
                          "items": [
                            {
                              "id": "normal",
                              "status": "confirmed",
                              "summary": "Normal",
                              "start": {"dateTime": "2026-07-01T09:00:00+09:00"},
                              "end": {"dateTime": "2026-07-01T10:00:00+09:00"}
                            },
                            {
                              "id": "master",
                              "status": "confirmed",
                              "recurrence": ["RRULE:FREQ=DAILY"],
                              "start": {"dateTime": "2026-07-01T09:00:00+09:00"},
                              "end": {"dateTime": "2026-07-01T10:00:00+09:00"}
                            },
                            {
                              "id": "exception",
                              "status": "confirmed",
                              "recurringEventId": "master",
                              "start": {"dateTime": "2026-07-02T09:00:00+09:00"},
                              "end": {"dateTime": "2026-07-02T10:00:00+09:00"}
                            },
                            {
                              "id": "cancelled",
                              "status": "cancelled"
                            }
                          ]
                        }
                        """,
                objectMapper
        );

        // then
        assertThat(page.items()).hasSize(4);
        assertThat(page.items().get(0).isRecurring()).isFalse();
        assertThat(page.items().get(1).isRecurring()).isTrue();
        assertThat(page.items().get(2).isRecurring()).isTrue();
        assertThat(page.items().get(3).isCancelled()).isTrue();
        assertThat(page.nextSyncToken()).isEqualTo("next-token");
    }

    @Test
    @DisplayName("nextPageToken과 nextSyncToken을 함께 반환한 page는 invalid response다")
    void givenConflictingPaginationTokens_whenParse_thenRejectsResponse() {
        // when, then
        assertThatThrownBy(() -> GoogleCalendarEventPage.fromJson(
                """
                        {
                          "nextPageToken": "page-2",
                          "nextSyncToken": "next-token",
                          "items": []
                        }
                        """,
                objectMapper
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
    }

    @Test
    @DisplayName("provider updated 시각이 RFC 3339 형식이 아니면 invalid response다")
    void givenMalformedUpdatedAt_whenParse_thenRejectsResponse() {
        // when, then
        assertThatThrownBy(() -> GoogleCalendarEventPage.fromJson(
                """
                        {
                          "nextSyncToken": "next-token",
                          "items": [
                            {
                              "id": "normal",
                              "status": "confirmed",
                              "updated": "not-an-instant",
                              "start": {"dateTime": "2026-07-01T09:00:00+09:00"},
                              "end": {"dateTime": "2026-07-01T10:00:00+09:00"}
                            }
                          ]
                        }
                        """,
                objectMapper
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
    }
}
