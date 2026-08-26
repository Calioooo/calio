package com.calio.calendar.aicalendar.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.aicalendar.config.CalendarAIProperties;
import com.calio.calendar.aicalendar.domain.CalendarMutationScope;
import com.calio.calendar.aicalendar.domain.CalendarMutationType;
import com.calio.calendar.aicalendar.domain.CalendarMutationOperation;
import com.calio.calendar.aicalendar.service.CalendarAgentObservationService;
import com.calio.calendar.aicalendar.service.CalendarMutationService;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationPreview;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarAgentToolRequestContext;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarLookupToolRequest;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarMutationToolRequest;
import com.calio.calendar.aicalendar.service.tool.dto.FreeTimeSearchToolRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.event.service.dto.CalendarFreeTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.model.ToolContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarAgentToolsTest {

    @Mock
    private EventService eventService;

    @Mock
    private CalendarAgentObservationService observationService;

    @Mock
    private CalendarMutationService mutationService;

    @Test
    @DisplayName("agenda tool은 EventResponse를 그대로 반환한다")
    void givenCalendarEvents_whenLookup_thenReturnsEventResponse() {
        // given
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(timedEvent()));
        CalendarAgentTools tools = tools();

        // when
        var result = tools.lookupCalendarEvents(
                new CalendarLookupToolRequest("2026-07-01", "2026-07-01"),
                toolContext(tools)
        );

        // then
        assertThat(result).singleElement().satisfies(event -> {
            assertThat(event.title()).isEqualTo("Planning");
            assertThat(event.description()).isEqualTo("Planning details");
            assertThat(event.allDay()).isFalse();
            assertThat(event.id()).isEqualTo(1L);
        });
        verify(eventService).listEvents(any(), any(), any());
    }

    @Test
    @DisplayName("free-time tool은 EventService가 계산한 빈 시간을 반환한다")
    void givenAvailableTimes_whenFindFreeTime_thenReturnsEventServiceResult() {
        // given
        when(eventService.findAvailableTimes(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        new CalendarFreeTime(
                                "2026-07-01T09:00:00Z",
                                "2026-07-01T10:00:00Z",
                                List.of("Holiday")
                        ),
                        new CalendarFreeTime(
                                "2026-07-01T11:00:00Z",
                                "2026-07-01T12:00:00Z",
                                List.of("Holiday")
                        )
                ));
        CalendarAgentTools tools = tools();

        // when
        var result = tools.findCalendarFreeTime(
                new FreeTimeSearchToolRequest(
                        "2026-07-01",
                        "2026-07-01",
                        "09:00",
                        "12:00",
                        60
                ),
                toolContext(tools)
        );

        // then
        assertThat(result.freeTimes()).hasSize(2);
        assertThat(result.freeTimes().getFirst().start()).isEqualTo("2026-07-01T09:00:00Z");
        assertThat(result.freeTimes().getFirst().end()).isEqualTo("2026-07-01T10:00:00Z");
        assertThat(result.freeTimes().getFirst().allDayNotices()).containsExactly("Holiday");
        assertThat(result.freeTimes().get(1).start()).isEqualTo("2026-07-01T11:00:00Z");
        assertThat(result.freeTimes().get(1).end()).isEqualTo("2026-07-01T12:00:00Z");
        verify(eventService).findAvailableTimes(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("mutation preview tool은 변경하지 않고 Preview 결과를 수집한다")
    void givenMutationRequest_whenPreview_thenCollectsPreviewWithoutApplyingChange() {
        // given
        CalendarAgentTools tools = tools();
        CalendarAgentToolResultCollector collector = new CalendarAgentToolResultCollector();
        CalendarMutationPreview preview = new CalendarMutationPreview(
                CalendarMutationType.UPDATE,
                CalendarMutationScope.EVENT,
                timedEvent(),
                timedEvent()
        );
        when(mutationService.preview(any(), any())).thenReturn(preview);

        // when
        CalendarMutationPreview result = tools.previewCalendarMutation(
                updateEventRequest(),
                toolContext(tools, collector)
        );

        // then
        assertThat(result).isEqualTo(preview);
        assertThat(collector.mutationPreviews()).containsExactly(preview);
        verify(mutationService).preview(1L, updateEventRequest());
        verify(mutationService, org.mockito.Mockito.never()).apply(any(), any());
    }

    @Test
    @DisplayName("mutation apply tool은 변경된 Event 결과를 수집한다")
    void givenConfirmedMutation_whenApply_thenCollectsChangedEvents() {
        // given
        CalendarAgentTools tools = tools();
        CalendarAgentToolResultCollector collector = new CalendarAgentToolResultCollector();
        when(mutationService.apply(any(), any())).thenReturn(List.of(timedEvent()));

        // when
        List<EventResponse> result = tools.applyCalendarMutation(
                updateEventRequest(),
                toolContext(tools, collector)
        );

        // then
        assertThat(result).containsExactly(timedEvent());
        assertThat(collector.events()).containsExactly(timedEvent());
        verify(mutationService).apply(1L, updateEventRequest());
    }

    @Test
    @DisplayName("변경 적용 결과는 앞선 조회 결과를 대체한다")
    void givenLookupBeforeConfirmedMutation_whenApply_thenCollectsOnlyChangedEvents() {
        // given
        CalendarAgentTools tools = tools();
        CalendarAgentToolResultCollector collector = new CalendarAgentToolResultCollector();
        ToolContext toolContext = toolContext(tools, collector);
        EventResponse changedEvent = new EventResponse(
                1L,
                "Updated planning",
                "Updated planning details",
                Instant.parse("2026-07-01T11:00:00Z"),
                Instant.parse("2026-07-01T12:00:00Z"),
                false,
                "UTC",
                false,
                null,
                false,
                null,
                null,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z")
        );
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(timedEvent()));
        when(mutationService.apply(any(), any())).thenReturn(List.of(changedEvent));

        // when
        tools.lookupCalendarEvents(new CalendarLookupToolRequest("2026-07-01", "2026-07-01"), toolContext);
        tools.applyCalendarMutation(updateEventRequest(), toolContext);

        // then
        assertThat(collector.events()).containsExactly(changedEvent);
    }

    @Test
    @DisplayName("tool 호출 제한은 다른 요청의 ToolContext와 공유하지 않는다")
    void givenSeparateToolContexts_whenOneRequestExceedsCallLimit_thenAnotherRequestCanCallTool() {
        // given
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(timedEvent()));
        CalendarAIProperties properties = new CalendarAIProperties();
        properties.setMaxToolCalls(1);
        CalendarAgentTools tools = new CalendarAgentTools(
                eventService,
                mutationService,
                properties,
                observationService
        );
        CalendarLookupToolRequest request = new CalendarLookupToolRequest("2026-07-01", "2026-07-01");
        ToolContext firstRequest = toolContext(tools);

        // when, then
        tools.lookupCalendarEvents(request, firstRequest);
        assertThatThrownBy(() -> tools.lookupCalendarEvents(request, firstRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Calendar agent tool call limit exceeded.");

        assertThat(tools.lookupCalendarEvents(request, toolContext(tools))).isNotEmpty();
    }

    private CalendarAgentTools tools() {
        return new CalendarAgentTools(
                eventService,
                mutationService,
                new CalendarAIProperties(),
                observationService
        );
    }

    private ToolContext toolContext(CalendarAgentTools tools) {
        return toolContext(tools, new CalendarAgentToolResultCollector());
    }

    private ToolContext toolContext(
            CalendarAgentTools tools,
            CalendarAgentToolResultCollector collector
    ) {
        return new ToolContext(tools.createToolContext(
                new CalendarAgentToolRequestContext(
                        1L,
                        "conversation-id",
                        ZoneId.of("UTC")
                ),
                collector
        ));
    }

    private CalendarMutationToolRequest updateEventRequest() {
        return new CalendarMutationToolRequest(
                CalendarMutationOperation.UPDATE_EVENT,
                1L,
                "Planning",
                "Planning details",
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T11:00:00Z"),
                false,
                "UTC",
                null
        );
    }

    private EventResponse timedEvent() {
        return new EventResponse(
                1L,
                "Planning",
                "Planning details",
                Instant.parse("2026-07-01T10:00:00Z"),
                Instant.parse("2026-07-01T11:00:00Z"),
                false,
                "UTC",
                false,
                null,
                false,
                null,
                null,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z")
        );
    }

}
