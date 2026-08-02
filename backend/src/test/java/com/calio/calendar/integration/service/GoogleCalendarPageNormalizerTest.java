package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventItem;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTime;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.CancelledRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarPageNormalizerTest {

    private final GoogleCalendarEventMappingRepository eventMappingRepository =
            mock(GoogleCalendarEventMappingRepository.class);
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository =
            mock(GoogleCalendarRecurrenceEventMappingRepository.class);
    private final GoogleCalendarEventTimeNormalizer timeNormalizer =
            mock(GoogleCalendarEventTimeNormalizer.class);
    private final GoogleCalendarRecurrenceMapper recurrenceMapper =
            mock(GoogleCalendarRecurrenceMapper.class);
    private final GoogleCalendarEventsClient eventsClient = mock(GoogleCalendarEventsClient.class);
    private final GoogleCalendarAccessTokenService accessTokenService =
            mock(GoogleCalendarAccessTokenService.class);
    private GoogleCalendarPageNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new GoogleCalendarPageNormalizer(
                eventMappingRepository,
                recurrenceMappingRepository,
                timeNormalizer,
                recurrenceMapper,
                eventsClient,
                accessTokenService
        );
        when(eventMappingRepository.findAllByExternalIdentity(any(), any(), any()))
                .thenReturn(List.of());
        when(recurrenceMappingRepository.findAllByExternalIdentity(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("minimum cancelled exception은 active schedule 검증 없이 recurrence override로 분류한다")
    void givenMinimumCancelledException_whenNormalize_thenClassifiesBeforeActiveValidation() {
        // given
        GoogleCalendarEventItem item = cancelledException("exception-1", "recurrence-event-1");
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                mock(GoogleCalendarRecurrenceEventMapping.class);
        when(recurrenceEventMapping.getExternalEventId()).thenReturn("recurrence-event-1");
        when(recurrenceMappingRepository.findAllByExternalIdentity(any(), any(), any()))
                .thenReturn(List.of(recurrenceEventMapping));
        var override = new CancelledRecurrenceEventOverrideUpsert(
                "exception-1",
                "recurrence-event-1",
                Instant.parse("2026-07-01T09:00:00Z"),
                null,
                null
        );
        when(recurrenceMapper.mapRecurrenceOverride(item)).thenReturn(override);

        // when
        GoogleCalendarNormalizedPage page = normalizer.normalize(
                10L,
                page(item),
                new GoogleCalendarSyncRunContext("token")
        );

        // then
        assertThat(page.items()).containsExactly(override);
        verify(eventsClient, never()).getEvent(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 recurrence-event 조회는 run 전체에 cache되어 관련 override를 건너뛴다")
    void givenMissingRecurrenceEvent_whenNormalize_thenLooksUpOnceAndSkipsOverrides() {
        // given
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext("token");
        when(eventsClient.getEvent("token", "recurrence-event-1"))
                .thenReturn(Optional.empty());

        // when
        GoogleCalendarNormalizedPage first = normalizer.normalize(
                10L,
                page(cancelledException("exception-1", "recurrence-event-1")),
                context
        );
        GoogleCalendarNormalizedPage second = normalizer.normalize(
                10L,
                page(cancelledException("exception-2", "recurrence-event-1")),
                context
        );

        // then
        assertThat(first.items()).isEmpty();
        assertThat(second.items()).isEmpty();
        verify(eventsClient).getEvent("token", "recurrence-event-1");
    }

    @Test
    @DisplayName("조회한 recurrence-event는 관련 override보다 먼저 정규화한다")
    void givenOverrideBeforeRecurrenceEvent_whenNormalize_thenOrdersRecurrenceEventFirst() {
        // given
        GoogleCalendarEventItem exception =
                cancelledException("exception-1", "recurrence-event-1");
        GoogleCalendarEventItem recurrenceEventResponse =
                recurringEvent("recurrence-event-1");
        RecurrenceEventUpsert recurrenceEvent = new RecurrenceEventUpsert(
                "recurrence-event-1",
                null,
                null,
                "Daily",
                null,
                schedule(),
                List.of("RRULE:FREQ=DAILY")
        );
        var override = new CancelledRecurrenceEventOverrideUpsert(
                "exception-1",
                "recurrence-event-1",
                Instant.parse("2026-07-01T09:00:00Z"),
                null,
                null
        );
        when(eventsClient.getEvent("token", "recurrence-event-1"))
                .thenReturn(Optional.of(recurrenceEventResponse));
        when(recurrenceMapper.mapRecurrenceEvent(recurrenceEventResponse))
                .thenReturn(recurrenceEvent);
        when(recurrenceMapper.mapRecurrenceOverride(exception)).thenReturn(override);

        // when
        GoogleCalendarNormalizedPage page = normalizer.normalize(
                10L,
                page(exception),
                new GoogleCalendarSyncRunContext("token")
        );

        // then
        assertThat(page.items()).containsExactly(
                recurrenceEvent,
                override
        );
    }

    private GoogleCalendarEventPage page(GoogleCalendarEventItem item) {
        return new GoogleCalendarEventPage(List.of(item), null, "cursor", "UTC");
    }

    private GoogleCalendarEventItem cancelledException(String id, String recurrenceEventId) {
        return new GoogleCalendarEventItem(
                id,
                "cancelled",
                null,
                null,
                null,
                null,
                List.of(),
                recurrenceEventId,
                new GoogleCalendarEventTime(null, "2026-07-01T09:00:00Z", "UTC"),
                null,
                null
        );
    }

    private GoogleCalendarEventItem recurringEvent(String id) {
        return new GoogleCalendarEventItem(
                id,
                "confirmed",
                null,
                null,
                "Daily",
                null,
                List.of("RRULE:FREQ=DAILY"),
                null,
                null,
                new GoogleCalendarEventTime(null, "2026-07-01T09:00:00Z", "UTC"),
                new GoogleCalendarEventTime(null, "2026-07-01T10:00:00Z", "UTC")
        );
    }

    private NormalizedEventSchedule schedule() {
        return new NormalizedEventSchedule(
                Instant.parse("2026-07-01T09:00:00Z"),
                Instant.parse("2026-07-01T10:00:00Z"),
                false,
                "UTC"
        );
    }
}
