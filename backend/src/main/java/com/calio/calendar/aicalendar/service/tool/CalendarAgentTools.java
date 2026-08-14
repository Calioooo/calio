package com.calio.calendar.aicalendar.service.tool;

import com.calio.calendar.aicalendar.config.CalendarAIProperties;
import com.calio.calendar.aicalendar.service.CalendarAgentObservationService;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarAgentToolRequestContext;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarLookupToolRequest;
import com.calio.calendar.aicalendar.service.tool.dto.FreeTimeSearchToolRequest;
import com.calio.calendar.aicalendar.service.tool.dto.FreeTimeSearchToolResult;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import com.calio.calendar.event.service.dto.CalendarFreeTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CalendarAgentTools {

    private static final String LOOKUP_TOOL_NAME = "lookup_calendar_events";
    private static final String FREE_TIME_TOOL_NAME = "find_calendar_free_time";
    private static final String REQUEST_CONTEXT_KEY = "calendarAgentToolRequestContext";
    private static final String CALL_COUNTER_KEY = "calendarAgentToolCallCounter";
    private static final String RESULT_COLLECTOR_KEY = "calendarAgentToolResultCollector";

    private final EventService eventService;
    private final CalendarAIProperties properties;
    private final CalendarAgentObservationService observationService;

    public CalendarAgentTools(
            EventService eventService,
            CalendarAIProperties properties,
            CalendarAgentObservationService observationService
    ) {
        this.eventService = eventService;
        this.properties = properties;
        this.observationService = observationService;
    }

    public Map<String, Object> createToolContext(
            CalendarAgentToolRequestContext requestContext,
            CalendarAgentToolResultCollector resultCollector
    ) {
        return Map.of(
                REQUEST_CONTEXT_KEY, requestContext,
                CALL_COUNTER_KEY, new CalendarAgentToolCallCounter(),
                RESULT_COLLECTOR_KEY, resultCollector
        );
    }

    @Tool(
            name = LOOKUP_TOOL_NAME,
            description = "Look up the authenticated user's current Calio calendar events for an inclusive ISO date range."
    )
    public List<EventResponse> lookupCalendarEvents(
            CalendarLookupToolRequest request,
            ToolContext toolContext
    ) {
        CalendarAgentToolRequestContext requestContext = getRequestContext(toolContext);
        assertToolCallAvailable(toolContext);
        Instant startedAt = Instant.now();
        try {
            CalendarToolTimeRange range = CalendarToolTimeRange.from(
                    request.startDate(),
                    request.endDate(),
                    requestContext.timeZone(),
                    properties
            );
            List<EventResponse> events = getCalendarEvents(requestContext.accountId(), range);
            getResultCollector(toolContext).recordEvents(events);
            observationService.recordTool(
                    requestContext.conversationId(),
                    LOOKUP_TOOL_NAME,
                    "SUCCESS",
                    Duration.between(startedAt, Instant.now()),
                    events.size()
            );
            return events;
        } catch (RuntimeException exception) {
            observationService.recordTool(
                    requestContext.conversationId(),
                    LOOKUP_TOOL_NAME,
                    "FAILURE",
                    Duration.between(startedAt, Instant.now()),
                    0
            );
            throw exception;
        }
    }

    @Tool(
            name = FREE_TIME_TOOL_NAME,
            description = "Find free time in the authenticated user's current Calio calendar for an inclusive ISO date range, daily local time window, and minimum duration in minutes."
    )
    public FreeTimeSearchToolResult findCalendarFreeTime(
            FreeTimeSearchToolRequest request,
            ToolContext toolContext
    ) {
        CalendarAgentToolRequestContext requestContext = getRequestContext(toolContext);
        assertToolCallAvailable(toolContext);
        Instant startedAt = Instant.now();
        try {
            CalendarToolTimeRange range = CalendarToolTimeRange.from(
                    request.startDate(),
                    request.endDate(),
                    requestContext.timeZone(),
                    properties
            );
            LocalTime windowStart = LocalTime.parse(request.windowStart());
            LocalTime windowEnd = LocalTime.parse(request.windowEnd());
            List<CalendarFreeTime> freeTimes = eventService.findAvailableTimes(
                    requestContext.accountId(),
                    range.startDate(),
                    range.endDate(),
                    range.timeZone(),
                    windowStart,
                    windowEnd,
                    Duration.ofMinutes(request.minimumDurationMinutes())
            );
            getResultCollector(toolContext).recordFreeTimes(freeTimes);
            observationService.recordTool(
                    requestContext.conversationId(),
                    FREE_TIME_TOOL_NAME,
                    "SUCCESS",
                    Duration.between(startedAt, Instant.now()),
                    freeTimes.size()
            );
            return new FreeTimeSearchToolResult(freeTimes);
        } catch (RuntimeException exception) {
            observationService.recordTool(
                    requestContext.conversationId(),
                    FREE_TIME_TOOL_NAME,
                    "FAILURE",
                    Duration.between(startedAt, Instant.now()),
                    0
            );
            throw exception;
        }
    }

    private CalendarAgentToolRequestContext getRequestContext(ToolContext toolContext) {
        Object context = toolContext.getContext().get(REQUEST_CONTEXT_KEY);
        if (context instanceof CalendarAgentToolRequestContext requestContext) {
            return requestContext;
        }
        throw new IllegalStateException("Calendar agent tool request context is missing.");
    }

    private void assertToolCallAvailable(ToolContext toolContext) {
        Object context = toolContext.getContext().get(CALL_COUNTER_KEY);
        if (!(context instanceof CalendarAgentToolCallCounter callCounter)) {
            throw new IllegalStateException("Calendar agent tool call counter is missing.");
        }
        if (!callCounter.incrementWithin(properties.getMaxToolCalls())) {
            throw new IllegalStateException("Calendar agent tool call limit exceeded.");
        }
    }

    private CalendarAgentToolResultCollector getResultCollector(ToolContext toolContext) {
        Object collector = toolContext.getContext().get(RESULT_COLLECTOR_KEY);
        if (collector instanceof CalendarAgentToolResultCollector resultCollector) {
            return resultCollector;
        }
        throw new IllegalStateException("Calendar agent tool result collector is missing.");
    }

    private List<EventResponse> getCalendarEvents(
            Long accountId,
            CalendarToolTimeRange range
    ) {
        Instant from = range.startDate().atStartOfDay(range.timeZone()).toInstant();
        Instant to = range.endDate().plusDays(1).atStartOfDay(range.timeZone()).toInstant();
        return eventService.listEvents(accountId, from, to);
    }
}
