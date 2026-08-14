package com.calio.calendar.aicalendar.service.tool;

import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.dto.CalendarFreeTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CalendarAgentToolResultCollector {

    private final Set<EventResponse> events = new LinkedHashSet<>();
    private final Set<CalendarFreeTime> freeTimes = new LinkedHashSet<>();

    public void recordEvents(List<EventResponse> events) {
        this.events.addAll(events);
    }

    public void recordFreeTimes(List<CalendarFreeTime> freeTimes) {
        this.freeTimes.addAll(freeTimes);
    }

    public List<EventResponse> events() {
        return List.copyOf(events);
    }

    public List<CalendarFreeTime> freeTimes() {
        return List.copyOf(freeTimes);
    }
}
