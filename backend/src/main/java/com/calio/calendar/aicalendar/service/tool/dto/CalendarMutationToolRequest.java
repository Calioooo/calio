package com.calio.calendar.aicalendar.service.tool.dto;

import com.calio.calendar.aicalendar.domain.CalendarMutationOperation;
import java.time.Instant;
import org.springframework.ai.tool.annotation.ToolParam;

public record CalendarMutationToolRequest(
        @ToolParam(description = "Mutation operation. Choose CREATE_EVENT for creation, UPDATE_EVENT for one normal event update, or DELETE_EVENT for one normal event deletion.", required = true)
        CalendarMutationOperation operation,
        @ToolParam(description = "ID of the normal event. Required for UPDATE_EVENT and DELETE_EVENT. Obtain it from lookup_calendar_events.", required = false)
        Long eventId,
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
        @ToolParam(description = "Optional Calio tag ID for creation or update. Omit it to preserve the existing tag on updates.", required = false)
        Long tagId
) {
}
