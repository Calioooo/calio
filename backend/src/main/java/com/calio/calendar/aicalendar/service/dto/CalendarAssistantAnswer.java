package com.calio.calendar.aicalendar.service.dto;

import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.dto.CalendarFreeTime;
import java.util.List;

public record CalendarAssistantAnswer(
        String message,
        List<EventResponse> events,
        List<CalendarFreeTime> freeTimes,
        List<CalendarMutationPreview> mutationPreviews
) {

    public CalendarAssistantAnswer {
        events = List.copyOf(events);
        freeTimes = List.copyOf(freeTimes);
        mutationPreviews = List.copyOf(mutationPreviews);
    }

    public static CalendarAssistantAnswer withoutBlocks(String message) {
        return new CalendarAssistantAnswer(message, List.of(), List.of(), List.of());
    }
}
