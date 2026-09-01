package com.calio.calendar.aicalendar.service.tool.dto;

import com.calio.calendar.aicalendar.domain.CalendarMutationOperation;
import java.time.Instant;
import java.util.List;
import org.springframework.ai.tool.annotation.ToolParam;

public record CalendarMutationToolRequest(
        @ToolParam(description = "Mutation operation. Choose CREATE_EVENT, UPDATE_EVENT, or DELETE_EVENT for a normal event. Choose UPDATE_RECURRENCE_OCCURRENCE or DELETE_RECURRENCE_OCCURRENCE for one occurrence, and UPDATE_RECURRENCE_SERIES or DELETE_RECURRENCE_SERIES for the entire recurrence series.", required = true)
        CalendarMutationOperation operation,
        @ToolParam(description = "ID of the normal event. Required for UPDATE_EVENT and DELETE_EVENT. Obtain it from lookup_calendar_events.", required = false)
        Long eventId,
        @ToolParam(description = "ID of the recurrence event. Required for recurrence occurrence and entire series operations. Obtain it from lookup_calendar_events.", required = false)
        Long recurrenceId,
        @ToolParam(description = "Original UTC start instant of one recurrence occurrence. Required for UPDATE_RECURRENCE_OCCURRENCE and DELETE_RECURRENCE_OCCURRENCE. Obtain it from lookup_calendar_events.", required = false)
        Instant originStartAt,
        @ToolParam(description = "Event title. Required for CREATE_EVENT; optional replacement title for updates.", required = false)
        String title,
        @ToolParam(description = "Optional event description. For updates, omit it to preserve the existing description.", required = false)
        String description,
        @ToolParam(description = "UTC start instant. Required for CREATE_EVENT; optional replacement start for updates.", required = false)
        Instant startAt,
        @ToolParam(description = "UTC end instant. Required for CREATE_EVENT; optional replacement end for updates. When only a timed event start changes, preserve its existing duration by supplying the corresponding end.", required = false)
        Instant endAt,
        @ToolParam(description = "Whether the event is all-day. Required for CREATE_EVENT; optional replacement value for updates.", required = false)
        Boolean allDay,
        @ToolParam(description = "IANA timezone identifier. Required when creating a timed event; optional replacement timezone for updates.", required = false)
        String timeZone,
        @ToolParam(description = "Optional Calio tag ID for creation, normal event updates, or entire recurrence series updates. A single recurrence occurrence cannot change its tag. Omit it to preserve the existing tag on updates.", required = false)
        Long tagId,
        @ToolParam(description = "RFC 5545 recurrence rules. Required only when replacing recurrence rules for UPDATE_RECURRENCE_SERIES; omit to preserve existing rules.", required = false)
        List<String> recurrenceRules
) {
}
