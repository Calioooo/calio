package com.calio.calendar.aicalendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.aicalendar.domain.CalendarMutationScope;
import com.calio.calendar.aicalendar.domain.CalendarMutationType;
import com.calio.calendar.aicalendar.domain.CalendarMutationOperation;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarMutationToolRequest;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.service.RecurrenceEventService;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.service.TagService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarMutationServiceTest {

    @Mock
    private EventService eventService;

    @Mock
    private RecurrenceEventService recurrenceEventService;

    @Mock
    private TagService tagService;

    @Test
    @DisplayName("일정 수정 Preview는 기존 일정을 조회하지만 실제 수정은 실행하지 않는다")
    void givenEventUpdate_whenPreview_thenReturnsBeforeAndAfterWithoutChangingEvent() {
        // given
        EventResponse existingEvent = event("기존 회의", Instant.parse("2026-08-21T05:00:00Z"));
        when(eventService.getEvent(1L, 10L)).thenReturn(existingEvent);
        when(tagService.getTagOrDefault(1L, 1L)).thenReturn(new Tag(TagType.DEFAULT, "업무", "#64748B"));

        // when
        var preview = service().preview(1L, updateRequest());

        // then
        assertThat(preview.type()).isEqualTo(CalendarMutationType.UPDATE);
        assertThat(preview.scope()).isEqualTo(CalendarMutationScope.EVENT);
        assertThat(preview.before()).isEqualTo(existingEvent);
        assertThat(preview.after().title()).isEqualTo("변경 회의");
        assertThat(preview.after().startAt()).isEqualTo(Instant.parse("2026-08-21T06:00:00Z"));
        verify(eventService, never()).updateEvent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("확정된 일정 수정은 기존 EventService 수정 유스케이스를 호출한다")
    void givenConfirmedEventUpdate_whenApply_thenDelegatesToExistingEventService() {
        // given
        when(eventService.getEvent(1L, 10L)).thenReturn(event("기존 회의", Instant.parse("2026-08-21T05:00:00Z")));
        EventResponse updatedEvent = event("변경 회의", Instant.parse("2026-08-21T06:00:00Z"));
        when(eventService.updateEvent(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(updatedEvent);

        // when
        List<EventResponse> result = service().apply(1L, updateRequest());

        // then
        assertThat(result).containsExactly(updatedEvent);
        ArgumentCaptor<com.calio.calendar.event.controller.dto.UpdateEventRequest> requestCaptor =
                ArgumentCaptor.forClass(com.calio.calendar.event.controller.dto.UpdateEventRequest.class);
        verify(eventService).updateEvent(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(10L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().title()).isEqualTo("변경 회의");
        assertThat(requestCaptor.getValue().startAt()).isEqualTo(Instant.parse("2026-08-21T06:00:00Z"));
    }

    @Test
    @DisplayName("확정된 반복 회차 수정은 기존 RecurrenceEventService 수정 유스케이스를 호출한다")
    void givenConfirmedRecurrenceOccurrenceUpdate_whenApply_thenDelegatesToExistingRecurrenceService() {
        // given
        EventResponse existingOccurrence = recurrenceOccurrence(
                "기존 회의",
                Instant.parse("2026-08-21T05:00:00Z")
        );
        EventResponse updatedOccurrence = recurrenceOccurrence(
                "변경 회의",
                Instant.parse("2026-08-21T06:00:00Z")
        );
        when(eventService.listEvents(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(existingOccurrence));
        when(recurrenceEventService.updateRecurrenceOccurrence(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(updatedOccurrence);

        // when
        List<EventResponse> result = service().apply(1L, occurrenceUpdateRequest());

        // then
        assertThat(result).containsExactly(updatedOccurrence);
        ArgumentCaptor<UpdateRecurrenceOccurrenceRequest> requestCaptor = ArgumentCaptor.forClass(
                UpdateRecurrenceOccurrenceRequest.class
        );
        verify(recurrenceEventService).updateRecurrenceOccurrence(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(20L),
                requestCaptor.capture()
        );
        assertThat(requestCaptor.getValue().originStartAt())
                .isEqualTo(Instant.parse("2026-08-21T05:00:00Z"));
        assertThat(requestCaptor.getValue().startAt())
                .isEqualTo(Instant.parse("2026-08-21T06:00:00Z"));
    }

    @Test
    @DisplayName("반복 회차 태그 변경 Preview는 지원하지 않는 변경 오류를 반환한다")
    void givenOccurrenceTagChange_whenPreview_thenRejectsUnsupportedChange() {
        // given
        EventResponse existingOccurrence = recurrenceOccurrence(
                "기존 회의",
                Instant.parse("2026-08-21T05:00:00Z")
        );
        when(eventService.listEvents(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(existingOccurrence));
        CalendarMutationToolRequest request = new CalendarMutationToolRequest(
                CalendarMutationOperation.UPDATE_RECURRENCE_OCCURRENCE,
                null,
                20L,
                Instant.parse("2026-08-21T05:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                null,
                99L,
                null
        );

        // when, then
        assertThatThrownBy(() -> service().preview(1L, request))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_TAG_CHANGE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("전체 반복 일정 수정 Preview는 변경 전후 recurrence rule을 함께 반환한다")
    void givenSeriesRuleChange_whenPreview_thenIncludesBeforeAndAfterRules() {
        // given
        RecurrenceEventResponse existingSeries = new RecurrenceEventResponse(
                20L,
                "기존 회의",
                "기존 설명",
                false,
                Instant.parse("2026-08-21T05:00:00Z"),
                Instant.parse("2026-08-21T06:00:00Z"),
                "Asia/Seoul",
                List.of("RRULE:FREQ=WEEKLY;BYDAY=FR"),
                null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z")
        );
        when(recurrenceEventService.getRecurrenceEvent(1L, 20L)).thenReturn(existingSeries);
        CalendarMutationToolRequest request = new CalendarMutationToolRequest(
                CalendarMutationOperation.UPDATE_RECURRENCE_SERIES,
                null,
                20L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("RRULE:FREQ=DAILY")
        );

        // when
        var preview = service().preview(1L, request);

        // then
        assertThat(preview.scope()).isEqualTo(CalendarMutationScope.ENTIRE_SERIES);
        assertThat(preview.recurrence().before()).containsExactly("RRULE:FREQ=WEEKLY;BYDAY=FR");
        assertThat(preview.recurrence().after()).containsExactly("RRULE:FREQ=DAILY");
    }

    private CalendarMutationService service() {
        return new CalendarMutationService(eventService, recurrenceEventService, tagService);
    }

    private CalendarMutationToolRequest updateRequest() {
        return new CalendarMutationToolRequest(
                CalendarMutationOperation.UPDATE_EVENT,
                10L,
                null,
                null,
                "변경 회의",
                "변경된 설명",
                Instant.parse("2026-08-21T06:00:00Z"),
                Instant.parse("2026-08-21T07:00:00Z"),
                false,
                "Asia/Seoul",
                1L,
                null
        );
    }

    private CalendarMutationToolRequest occurrenceUpdateRequest() {
        return new CalendarMutationToolRequest(
                CalendarMutationOperation.UPDATE_RECURRENCE_OCCURRENCE,
                null,
                20L,
                Instant.parse("2026-08-21T05:00:00Z"),
                "변경 회의",
                "변경된 설명",
                Instant.parse("2026-08-21T06:00:00Z"),
                Instant.parse("2026-08-21T07:00:00Z"),
                false,
                "Asia/Seoul",
                null,
                null
        );
    }

    private EventResponse event(String title, Instant startAt) {
        return new EventResponse(
                10L,
                title,
                "기존 설명",
                startAt,
                startAt.plusSeconds(3600),
                false,
                "Asia/Seoul",
                false,
                null,
                false,
                null,
                null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z")
        );
    }

    private EventResponse recurrenceOccurrence(String title, Instant startAt) {
        return new EventResponse(
                null,
                title,
                "기존 설명",
                startAt,
                startAt.plusSeconds(3600),
                false,
                "Asia/Seoul",
                false,
                20L,
                true,
                null,
                Instant.parse("2026-08-21T05:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z")
        );
    }
}
