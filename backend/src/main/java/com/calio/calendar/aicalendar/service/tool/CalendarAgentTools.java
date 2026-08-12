package com.calio.calendar.aicalendar.service.tool;

import com.calio.calendar.aicalendar.config.CalendarAIProperties;
import com.calio.calendar.aicalendar.service.CalendarAgentObservationService;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarFreeTime;
import com.calio.calendar.aicalendar.service.tool.dto.CalendarLookupToolRequest;
import com.calio.calendar.aicalendar.service.tool.dto.FreeTimeSearchToolRequest;
import com.calio.calendar.aicalendar.service.tool.dto.FreeTimeSearchToolResult;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.tool.annotation.Tool;

public class CalendarAgentTools {

    private static final String LOOKUP_TOOL_NAME = "lookup_calendar_events";
    private static final String FREE_TIME_TOOL_NAME = "find_calendar_free_time";

    private final Long accountId;
    private final String conversationId;
    private final ZoneId timeZone;
    private final EventService eventService;
    private final CalendarAIProperties properties;
    private final CalendarAgentObservationService observationService;
    private final AtomicInteger callCount = new AtomicInteger();

    public CalendarAgentTools(
            Long accountId,
            String conversationId,
            ZoneId timeZone,
            EventService eventService,
            CalendarAIProperties properties,
            CalendarAgentObservationService observationService
    ) {
        this.accountId = accountId;
        this.conversationId = conversationId;
        this.timeZone = timeZone;
        this.eventService = eventService;
        this.properties = properties;
        this.observationService = observationService;
    }

    @Tool(
            name = LOOKUP_TOOL_NAME,
            description = "Look up the authenticated user's current Calio calendar events for an inclusive ISO date range."
    )
    public List<EventResponse> lookupCalendarEvents(CalendarLookupToolRequest request) {
        assertToolCallAvailable();
        Instant startedAt = Instant.now();
        try {
            CalendarToolTimeRange range = CalendarToolTimeRange.from(
                    request.startDate(),
                    request.endDate(),
                    timeZone,
                    properties
            );
            List<EventResponse> events = getCalendarEvents(range);
            observationService.recordTool(
                    conversationId,
                    LOOKUP_TOOL_NAME,
                    "SUCCESS",
                    Duration.between(startedAt, Instant.now()),
                    events.size()
            );
            return events;
        } catch (RuntimeException exception) {
            observationService.recordTool(
                    conversationId,
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
    public FreeTimeSearchToolResult findCalendarFreeTime(FreeTimeSearchToolRequest request) {
        assertToolCallAvailable();
        Instant startedAt = Instant.now();
        try {
            CalendarToolTimeRange range = CalendarToolTimeRange.from(
                    request.startDate(),
                    request.endDate(),
                    timeZone,
                    properties
            );
            LocalTime windowStart = LocalTime.parse(request.windowStart());
            LocalTime windowEnd = LocalTime.parse(request.windowEnd());
            List<CalendarFreeTime> freeTimes = findFreeTimes(
                    range,
                    windowStart,
                    windowEnd,
                    Duration.ofMinutes(request.minimumDurationMinutes())
            );
            observationService.recordTool(
                    conversationId,
                    FREE_TIME_TOOL_NAME,
                    "SUCCESS",
                    Duration.between(startedAt, Instant.now()),
                    freeTimes.size()
            );
            return new FreeTimeSearchToolResult(freeTimes);
        } catch (RuntimeException exception) {
            observationService.recordTool(
                    conversationId,
                    FREE_TIME_TOOL_NAME,
                    "FAILURE",
                    Duration.between(startedAt, Instant.now()),
                    0
            );
            throw exception;
        }
    }

    private void assertToolCallAvailable() {
        if (callCount.incrementAndGet() > properties.getMaxToolCalls()) {
            throw new IllegalStateException("Calendar agent tool call limit exceeded.");
        }
    }

    private List<EventResponse> getCalendarEvents(CalendarToolTimeRange range) {
        Instant from = range.startDate().atStartOfDay(range.timeZone()).toInstant();
        Instant to = range.endDate().plusDays(1).atStartOfDay(range.timeZone()).toInstant();
        return eventService.listEvents(accountId, from, to);
    }

    private List<CalendarFreeTime> findFreeTimes(
            CalendarToolTimeRange range,
            LocalTime windowStart,
            LocalTime windowEnd,
            Duration minimumDuration
    ) {
        if (!windowStart.isBefore(windowEnd) || minimumDuration.isZero() || minimumDuration.isNegative()) {
            throw new IllegalArgumentException("Availability window and duration must be positive.");
        }
        List<EventResponse> events = getCalendarEvents(range);
        List<CalendarFreeTime> freeTimes = new ArrayList<>();
        for (LocalDate date = range.startDate(); !date.isAfter(range.endDate()); date = date.plusDays(1)) {
            addFreeTimesForDate(events, date, windowStart, windowEnd, minimumDuration, freeTimes);
        }
        return freeTimes;
    }

    private void addFreeTimesForDate(
            List<EventResponse> events,
            LocalDate date,
            LocalTime windowStart,
            LocalTime windowEnd,
            Duration minimumDuration,
            List<CalendarFreeTime> freeTimes
    ) {
        Instant dayWindowStart = LocalDateTime.of(date, windowStart).atZone(timeZone).toInstant();
        Instant dayWindowEnd = LocalDateTime.of(date, windowEnd).atZone(timeZone).toInstant();
        List<TimeInterval> occupiedTimes = events.stream()
                .filter(event -> !event.allDay())
                .map(event -> TimeInterval.overlap(event.startAt(), event.endAt(), dayWindowStart, dayWindowEnd))
                .filter(TimeInterval::hasDuration)
                .sorted(Comparator.comparing(TimeInterval::start))
                .toList();
        List<String> allDayNotices = events.stream()
                .filter(EventResponse::allDay)
                .filter(event -> occursOn(event, date))
                .map(EventResponse::title)
                .toList();
        addAvailableGaps(dayWindowStart, dayWindowEnd, occupiedTimes, minimumDuration, allDayNotices, freeTimes);
    }

    private void addAvailableGaps(
            Instant windowStart,
            Instant windowEnd,
            List<TimeInterval> occupiedTimes,
            Duration minimumDuration,
            List<String> allDayNotices,
            List<CalendarFreeTime> freeTimes
    ) {
        Instant cursor = windowStart;
        for (TimeInterval occupiedTime : occupiedTimes) {
            if (Duration.between(cursor, occupiedTime.start()).compareTo(minimumDuration) >= 0) {
                freeTimes.add(toFreeTime(cursor, occupiedTime.start(), allDayNotices));
            }
            if (occupiedTime.end().isAfter(cursor)) {
                cursor = occupiedTime.end();
            }
        }
        if (Duration.between(cursor, windowEnd).compareTo(minimumDuration) >= 0) {
            freeTimes.add(toFreeTime(cursor, windowEnd, allDayNotices));
        }
    }

    private CalendarFreeTime toFreeTime(Instant start, Instant end, List<String> allDayNotices) {
        return new CalendarFreeTime(
                formatEventTime(start, false),
                formatEventTime(end, false),
                allDayNotices
        );
    }

    private boolean occursOn(EventResponse event, LocalDate date) {
        LocalDate startDate = event.startAt().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = event.endAt().atZone(ZoneOffset.UTC).toLocalDate();
        return !date.isBefore(startDate) && date.isBefore(endDate);
    }

    private String formatEventTime(Instant instant, boolean allDay) {
        if (allDay) {
            return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
        }
        return instant.atZone(timeZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private record TimeInterval(Instant start, Instant end) {

        private static TimeInterval overlap(Instant start, Instant end, Instant rangeStart, Instant rangeEnd) {
            Instant overlapStart = start.isAfter(rangeStart) ? start : rangeStart;
            Instant overlapEnd = end.isBefore(rangeEnd) ? end : rangeEnd;
            return new TimeInterval(overlapStart, overlapEnd);
        }

        private boolean hasDuration() {
            return start.isBefore(end);
        }
    }
}
