package com.calio.calendar.aicalendar.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.aicalendar.config.CalendarAIProperties;
import com.calio.calendar.aicalendar.service.CalendarAgentObservationService;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarAgentToolRequestContext;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarLookupToolRequest;
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
    @DisplayName("tool 호출 제한은 다른 요청의 ToolContext와 공유하지 않는다")
    void givenSeparateToolContexts_whenOneRequestExceedsCallLimit_thenAnotherRequestCanCallTool() {
        // given
        when(eventService.listEvents(any(), any(), any())).thenReturn(List.of(timedEvent()));
        CalendarAIProperties properties = new CalendarAIProperties();
        properties.setMaxToolCalls(1);
        CalendarAgentTools tools = new CalendarAgentTools(eventService, properties, observationService);
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
                new CalendarAIProperties(),
                observationService
        );
    }

    private ToolContext toolContext(CalendarAgentTools tools) {
        return new ToolContext(tools.createToolContext(
                new CalendarAgentToolRequestContext(1L, "conversation-id", ZoneId.of("UTC")),
                new CalendarAgentToolResultCollector()
        ));
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
