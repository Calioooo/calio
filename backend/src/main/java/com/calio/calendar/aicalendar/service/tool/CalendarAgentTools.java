package com.calio.calendar.aicalendar.service.tool;

import com.calio.calendar.aicalendar.config.CalendarAIProperties;
import com.calio.calendar.aicalendar.service.CalendarAgentObservationService;
import com.calio.calendar.aicalendar.service.CalendarMutationService;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationPreview;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarAgentToolRequestContext;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarLookupToolRequest;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarMutationToolRequest;
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
import java.util.function.Function;
import java.util.function.ToIntFunction;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class CalendarAgentTools {

    private static final String LOOKUP_TOOL_NAME = "lookup_calendar_events";
    private static final String FREE_TIME_TOOL_NAME = "find_calendar_free_time";
    private static final String PREVIEW_MUTATION_TOOL_NAME = "preview_calendar_mutation";
    private static final String APPLY_MUTATION_TOOL_NAME = "apply_calendar_mutation";
    private static final String REQUEST_CONTEXT_KEY = "calendarAgentToolRequestContext";
    private static final String CALL_COUNTER_KEY = "calendarAgentToolCallCounter";
    private static final String RESULT_COLLECTOR_KEY = "calendarAgentToolResultCollector";

    private final EventService eventService;
    private final CalendarMutationService mutationService;
    private final CalendarAIProperties properties;
    private final CalendarAgentObservationService observationService;

    public CalendarAgentTools(
            EventService eventService,
            CalendarMutationService mutationService,
            CalendarAIProperties properties,
            CalendarAgentObservationService observationService
    ) {
        this.eventService = eventService;
        this.mutationService = mutationService;
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
            description = "Look up the authenticated user's current Calio calendar events for an inclusive ISO date range. Use this before a calendar update or deletion when the event ID must be resolved, and use its returned ID for a later mutation Preview."
    )
    public List<EventResponse> lookupCalendarEvents(
            CalendarLookupToolRequest request,
            ToolContext toolContext
    ) {
        return runTool(LOOKUP_TOOL_NAME, toolContext, requestContext -> {
            CalendarToolTimeRange range = CalendarToolTimeRange.from(
                    request.startDate(),
                    request.endDate(),
                    requestContext.timeZone(),
                    properties
            );
            List<EventResponse> events = getCalendarEvents(requestContext.accountId(), range);
            getResultCollector(toolContext).recordEvents(events);
            return events;
        }, List::size);
    }

    @Tool(
            name = FREE_TIME_TOOL_NAME,
            description = "Find free time in the authenticated user's current Calio calendar for an inclusive ISO date range, daily local time window, and minimum duration in minutes."
    )
    public FreeTimeSearchToolResult findCalendarFreeTime(
            FreeTimeSearchToolRequest request,
            ToolContext toolContext
    ) {
        return runTool(FREE_TIME_TOOL_NAME, toolContext, requestContext -> {
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
            return new FreeTimeSearchToolResult(freeTimes);
        }, result -> result.freeTimes().size());
    }

    @Tool(
            name = PREVIEW_MUTATION_TOOL_NAME,
            description = "Prepare a non-mutating Preview for one fully specified and unambiguous normal Calio event creation, update, or deletion. For CREATE_EVENT, a user-stated event name and date or time resolved from system context are complete input; do not ask the user to confirm a relative date or ask permission to prepare the Preview. For UPDATE_EVENT or DELETE_EVENT, use an eventId from lookup_calendar_events. Use this immediately after resolving exactly one target."
    )
    public CalendarMutationPreview previewCalendarMutation(
            CalendarMutationToolRequest request,
            ToolContext toolContext
    ) {
        return runTool(PREVIEW_MUTATION_TOOL_NAME, toolContext, requestContext -> {
            CalendarMutationPreview preview = mutationService.preview(requestContext.accountId(), request);
            getResultCollector(toolContext).recordMutationPreview(preview);
            return preview;
        }, ignored -> 1);
    }

    @Tool(
            name = APPLY_MUTATION_TOOL_NAME,
            description = "Apply one previously previewed Calio calendar change. This tool changes calendar data. Before calling it, consider every earlier assistant response state: a later Preview does not replace or cancel an earlier Preview. Use it only when a later user message explicitly approves exactly one Preview. A message that supplies missing information or selects an event is not approval. If multiple unapplied Previews exist, do not call this tool until the user identifies one."
    )
    public List<EventResponse> applyCalendarMutation(
            CalendarMutationToolRequest request,
            ToolContext toolContext
    ) {
        return runTool(APPLY_MUTATION_TOOL_NAME, toolContext, requestContext -> {
            List<EventResponse> events = mutationService.apply(requestContext.accountId(), request);
            getResultCollector(toolContext).replaceEvents(events);
            return events;
        }, List::size);
    }

    private <T> T runTool(
            String toolName,
            ToolContext toolContext,
            Function<CalendarAgentToolRequestContext, T> action,
            ToIntFunction<T> resultCount
    ) {
        CalendarAgentToolRequestContext requestContext = getRequestContext(toolContext);
        assertToolCallAvailable(toolContext);
        Instant startedAt = Instant.now();
        try {
            T result = action.apply(requestContext);
            observationService.recordTool(
                    requestContext.conversationId(),
                    toolName,
                    "SUCCESS",
                    Duration.between(startedAt, Instant.now()),
                    resultCount.applyAsInt(result)
            );
            return result;
        } catch (RuntimeException exception) {
            observationService.recordTool(
                    requestContext.conversationId(),
                    toolName,
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
