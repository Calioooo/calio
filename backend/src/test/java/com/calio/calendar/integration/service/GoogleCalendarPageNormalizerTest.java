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
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceMasterUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.CancelledRecurrenceOverrideResult;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.RecurrenceEventResult;
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
    @DisplayName("minimum cancelled exception은 active schedule 검증 없이 mapped parent override로 분류한다")
    void givenMinimumCancelledException_whenNormalize_thenClassifiesBeforeActiveValidation() {
        // given
        GoogleCalendarEventItem item = cancelledException("exception-1", "master-1");
        GoogleCalendarRecurrenceEventMapping parentMapping =
                mock(GoogleCalendarRecurrenceEventMapping.class);
        when(parentMapping.getExternalEventId()).thenReturn("master-1");
        when(recurrenceMappingRepository.findAllByExternalIdentity(any(), any(), any()))
                .thenReturn(List.of(parentMapping));
        var result = new CancelledRecurrenceOverrideResult(
                "exception-1",
                "master-1",
                Instant.parse("2026-07-01T09:00:00Z"),
                null,
                null
        );
        when(recurrenceMapper.mapRecurrenceOverride(item)).thenReturn(result);

        // when
        GoogleCalendarNormalizedPage page = normalizer.normalize(
                10L,
                page(item),
                new GoogleCalendarSyncRunContext("token")
        );

        // then
        assertThat(page.items()).containsExactly(new RecurrenceOverrideUpsert(result));
        verify(eventsClient, never()).getEvent(any(), any());
    }

    @Test
    @DisplayName("explicit 404 parent outcome은 run 전체에 cache되어 dependent exceptions만 skip한다")
    void givenMissingParentAcrossPages_whenNormalize_thenLooksUpParentOnceAndSkipsExceptions() {
        // given
        GoogleCalendarSyncRunContext context = new GoogleCalendarSyncRunContext("token");
        when(eventsClient.getEvent("token", "master-1")).thenReturn(Optional.empty());

        // when
        GoogleCalendarNormalizedPage first = normalizer.normalize(
                10L,
                page(cancelledException("exception-1", "master-1")),
                context
        );
        GoogleCalendarNormalizedPage second = normalizer.normalize(
                10L,
                page(cancelledException("exception-2", "master-1")),
                context
        );

        // then
        assertThat(first.items()).isEmpty();
        assertThat(second.items()).isEmpty();
        verify(eventsClient).getEvent("token", "master-1");
    }

    @Test
    @DisplayName("lookup으로 확보한 active recurring parent는 dependent exception보다 먼저 정규화한다")
    void givenExceptionBeforeMissingParent_whenNormalize_thenOrdersFetchedParentFirst() {
        // given
        GoogleCalendarEventItem exception = cancelledException("exception-1", "master-1");
        GoogleCalendarEventItem parent = recurringMaster("master-1");
        RecurrenceEventResult parentResult = new RecurrenceEventResult(
                "master-1",
                null,
                null,
                "Daily",
                null,
                schedule(),
                List.of("RRULE:FREQ=DAILY")
        );
        var overrideResult = new CancelledRecurrenceOverrideResult(
                "exception-1",
                "master-1",
                Instant.parse("2026-07-01T09:00:00Z"),
                null,
                null
        );
        when(eventsClient.getEvent("token", "master-1")).thenReturn(Optional.of(parent));
        when(recurrenceMapper.mapRecurrenceEvent(parent)).thenReturn(parentResult);
        when(recurrenceMapper.mapRecurrenceOverride(exception)).thenReturn(overrideResult);

        // when
        GoogleCalendarNormalizedPage page = normalizer.normalize(
                10L,
                page(exception),
                new GoogleCalendarSyncRunContext("token")
        );

        // then
        assertThat(page.items()).containsExactly(
                new RecurrenceMasterUpsert(parentResult),
                new RecurrenceOverrideUpsert(overrideResult)
        );
    }

    private GoogleCalendarEventPage page(GoogleCalendarEventItem item) {
        return new GoogleCalendarEventPage(List.of(item), null, "cursor", "UTC");
    }

    private GoogleCalendarEventItem cancelledException(String id, String parentId) {
        return new GoogleCalendarEventItem(
                id,
                "cancelled",
                null,
                null,
                null,
                null,
                List.of(),
                parentId,
                new GoogleCalendarEventTime(null, "2026-07-01T09:00:00Z", "UTC"),
                null,
                null
        );
    }

    private GoogleCalendarEventItem recurringMaster(String id) {
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
