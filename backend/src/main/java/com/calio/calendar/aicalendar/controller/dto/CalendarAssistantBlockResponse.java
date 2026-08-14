package com.calio.calendar.aicalendar.controller.dto;

import com.calio.calendar.aicalendar.domain.CalendarAssistantBlockType;
import com.calio.calendar.event.controller.dto.EventResponse;
import java.util.List;

public record CalendarAssistantBlockResponse<T>(
        CalendarAssistantBlockType type,
        List<T> items
) {

    public static CalendarAssistantBlockResponse<EventResponse> events(List<EventResponse> events) {
        return new CalendarAssistantBlockResponse<>(CalendarAssistantBlockType.EVENTS, events);
    }

    public static CalendarAssistantBlockResponse<FreeTimeResponse> freeTimes(
            List<FreeTimeResponse> freeTimes
    ) {
        return new CalendarAssistantBlockResponse<>(CalendarAssistantBlockType.FREE_TIMES, freeTimes);
    }
}
