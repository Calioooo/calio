package com.calio.calendar.integration.sync.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer;
import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventPage;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTimeResponse;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.integration.sync.GoogleCalendarSyncRunContext;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.CancelledRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventCancellation;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarPageNormalizerTest {

    private final GoogleCalendarEventMappingQueryService eventMappingQueryService =
            mock(GoogleCalendarEventMappingQueryService.class);
    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService =
            mock(GoogleCalendarRecurrenceMappingQueryService.class);
    private final GoogleCalendarEventTimeNormalizer timeNormalizer =
            mock(GoogleCalendarEventTimeNormalizer.class);
    private final GoogleCalendarRecurrenceMapper recurrenceMapper =
            mock(GoogleCalendarRecurrenceMapper.class);
    private final GoogleCalendarRecurrenceEventLoader recurrenceEventLoader =
            mock(GoogleCalendarRecurrenceEventLoader.class);
    private GoogleCalendarPageNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new GoogleCalendarPageNormalizer(
                eventMappingQueryService,
                recurrenceMappingQueryService,
                timeNormalizer,
                recurrenceMapper,
                recurrenceEventLoader
        );
        when(eventMappingQueryService.listEventMappings(any(), any(), any()))
                .thenReturn(List.of());
        when(recurrenceMappingQueryService.listRecurrenceEventMappings(any(), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("취소된 recurrence override는 시간이나 schedule을 검증하지 않는다")
    void givenDeletedRecurrenceOccurrence_whenNormalize_thenClassifiesAsOverride() {
        // given
        GoogleCalendarEventResponse item =
                deletedRecurrenceOccurrence("override-1", "recurrence-event-1");
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                mock(GoogleCalendarRecurrenceEventMapping.class);
        when(recurrenceEventMapping.getExternalEventId()).thenReturn("recurrence-event-1");
        when(recurrenceMappingQueryService.listRecurrenceEventMappings(any(), any(), any()))
                .thenReturn(List.of(recurrenceEventMapping));

        var override = new CancelledRecurrenceEventOverrideUpsert(
                "override-1",
                "recurrence-event-1",
                Instant.parse("2026-07-01T09:00:00Z"),
                "etag-override-1",
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
        verify(recurrenceEventLoader, never()).loadRecurrenceEvent(any(), any(), any());
    }

    @Test
    @DisplayName("recurrence override를 정규화할 때 연결된 recurrence event를 함께 조회한다")
    void givenOverrideBeforeRecurrenceEvent_whenNormalize_thenOrdersRecurrenceEventFirst() {
        // given
        GoogleCalendarEventResponse deletedOccurrence =
                deletedRecurrenceOccurrence("override-1", "recurrence-event-1");
        RecurrenceEventUpsert recurrenceEvent = new RecurrenceEventUpsert(
                "recurrence-event-1",
                "etag-recurrence-event-1",
                "Daily",
                null,
                schedule(),
                List.of("RRULE:FREQ=DAILY")
        );
        var override = new CancelledRecurrenceEventOverrideUpsert(
                "override-1",
                "recurrence-event-1",
                Instant.parse("2026-07-01T09:00:00Z"),
                "etag-override-1",
                null
        );
        when(recurrenceEventLoader.loadRecurrenceEvent(any(), any(), any()))
                .thenReturn(Optional.of(recurrenceEvent));
        when(recurrenceMapper.mapRecurrenceOverride(deletedOccurrence)).thenReturn(override);

        // when
        GoogleCalendarNormalizedPage page = normalizer.normalize(
                10L,
                page(deletedOccurrence),
                new GoogleCalendarSyncRunContext("token")
        );

        // then
        assertThat(page.items()).containsExactly(
                recurrenceEvent,
                override
        );
    }

    @Test
    @DisplayName("같은 page의 recurrence-event cancellation은 관련 override 뒤에 정규화한다")
    void givenCancellationBeforeOverride_whenNormalize_thenOrdersCancellationLast() {
        // given
        GoogleCalendarEventResponse cancellation = cancelledItem("recurrence-event-1");
        GoogleCalendarEventResponse deletedOccurrence =
                deletedRecurrenceOccurrence("override-1", "recurrence-event-1");
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                mock(GoogleCalendarRecurrenceEventMapping.class);
        when(recurrenceEventMapping.getExternalEventId()).thenReturn("recurrence-event-1");
        when(recurrenceMappingQueryService.listRecurrenceEventMappings(any(), any(), any()))
                .thenReturn(List.of(recurrenceEventMapping));
        var override = new CancelledRecurrenceEventOverrideUpsert(
                "override-1",
                "recurrence-event-1",
                Instant.parse("2026-07-01T09:00:00Z"),
                "etag-override-1",
                null
        );
        when(recurrenceMapper.mapRecurrenceOverride(deletedOccurrence)).thenReturn(override);

        // when
        GoogleCalendarNormalizedPage page = normalizer.normalize(
                10L,
                page(List.of(cancellation, deletedOccurrence)),
                new GoogleCalendarSyncRunContext("token")
        );

        // then
        assertThat(page.items()).containsExactly(
                override,
                new RecurrenceEventCancellation("recurrence-event-1")
        );
    }

    private GoogleCalendarEventPage page(GoogleCalendarEventResponse item) {
        return page(List.of(item));
    }

    private GoogleCalendarEventPage page(List<GoogleCalendarEventResponse> items) {
        return new GoogleCalendarEventPage(items, null, "cursor", "UTC");
    }

    private GoogleCalendarEventResponse cancelledItem(String id) {
        return new GoogleCalendarEventResponse(
                id,
                "cancelled",
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null
        );
    }

    private GoogleCalendarEventResponse deletedRecurrenceOccurrence(
            String id,
            String recurrenceEventId
    ) {
        return new GoogleCalendarEventResponse(
                id,
                "cancelled",
                null,
                null,
                null,
                null,
                List.of(),
                recurrenceEventId,
                new GoogleCalendarEventTimeResponse(null, "2026-07-01T09:00:00Z", "UTC"),
                null,
                null
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
