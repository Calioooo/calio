package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.GoogleCalendarUnauthorizedException;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarEventRequestServiceTest {

    private final GoogleCalendarEventsClient eventsClient =
            mock(GoogleCalendarEventsClient.class);
    private final GoogleCalendarAccessTokenService accessTokenService =
            mock(GoogleCalendarAccessTokenService.class);
    private final GoogleCalendarEventRequestService requestService =
            new GoogleCalendarEventRequestService(eventsClient, accessTokenService);

    @Test
    @DisplayName("일정 페이지 조회가 401이면 access token을 갱신하고 같은 페이지를 재요청한다")
    void givenUnauthorizedPageRequest_whenListEvents_thenRefreshesTokenAndRetries() {
        // given
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext("old-token");
        GoogleCalendarEventPage expected = new GoogleCalendarEventPage(
                List.of(), null, "next-sync-token", "UTC"
        );
        when(eventsClient.listEvents(
                "old-token",
                GoogleCalendarSyncMode.INCREMENTAL,
                "saved-sync-token",
                "page-2"
        )).thenThrow(unauthorized());
        when(accessTokenService.forceRefresh(10L)).thenReturn("new-token");
        when(eventsClient.listEvents(
                "new-token",
                GoogleCalendarSyncMode.INCREMENTAL,
                "saved-sync-token",
                "page-2"
        )).thenReturn(expected);

        // when
        GoogleCalendarEventPage actual = requestService.listEvents(
                10L,
                GoogleCalendarSyncMode.INCREMENTAL,
                "saved-sync-token",
                "page-2",
                context
        );

        // then
        assertThat(actual).isSameAs(expected);
        assertThat(context.accessToken()).isEqualTo("new-token");
        verify(accessTokenService).forceRefresh(10L);
    }

    @Test
    @DisplayName("FULL 페이지 조회에는 이전 sync token을 전달하지 않는다")
    void givenFullSync_whenListEvents_thenOmitsSavedSyncToken() {
        // given
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext("token");
        GoogleCalendarEventPage expected = new GoogleCalendarEventPage(
                List.of(), null, "next-sync-token", "UTC"
        );
        when(eventsClient.listEvents(
                "token",
                GoogleCalendarSyncMode.FULL,
                null,
                null
        )).thenReturn(expected);

        // when
        GoogleCalendarEventPage actual = requestService.listEvents(
                10L,
                GoogleCalendarSyncMode.FULL,
                "saved-sync-token",
                null,
                context
        );

        // then
        assertThat(actual).isSameAs(expected);
    }

    @Test
    @DisplayName("토큰 갱신 후 일정 페이지 재요청도 401이면 재연결 오류를 던진다")
    void givenRepeatedUnauthorizedPageRequest_whenListEvents_thenRequiresReconnect() {
        // given
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext("old-token");
        when(eventsClient.listEvents("old-token", GoogleCalendarSyncMode.FULL, null, null))
                .thenThrow(unauthorized());
        when(accessTokenService.forceRefresh(10L)).thenReturn("new-token");
        when(eventsClient.listEvents("new-token", GoogleCalendarSyncMode.FULL, null, null))
                .thenThrow(unauthorized());

        // when, then
        assertThatThrownBy(() -> requestService.listEvents(
                10L,
                GoogleCalendarSyncMode.FULL,
                null,
                null,
                context
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED));
    }

    @Test
    @DisplayName("단일 일정 조회도 401이면 갱신한 access token으로 재요청한다")
    void givenUnauthorizedEventRequest_whenGetEvent_thenRefreshesTokenAndRetries() {
        // given
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext("old-token");
        GoogleCalendarEventResponse expected = mock(GoogleCalendarEventResponse.class);
        when(eventsClient.getEvent("old-token", "event-1")).thenThrow(unauthorized());
        when(accessTokenService.forceRefresh(10L)).thenReturn("new-token");
        when(eventsClient.getEvent("new-token", "event-1"))
                .thenReturn(Optional.of(expected));

        // when
        Optional<GoogleCalendarEventResponse> actual = requestService.getEvent(
                10L,
                "event-1",
                context
        );

        // then
        assertThat(actual).containsSame(expected);
        assertThat(context.accessToken()).isEqualTo("new-token");
    }

    private GoogleCalendarUnauthorizedException unauthorized() {
        return new GoogleCalendarUnauthorizedException(new RuntimeException());
    }
}
