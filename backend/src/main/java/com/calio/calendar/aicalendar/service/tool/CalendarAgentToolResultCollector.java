package com.calio.calendar.aicalendar.service.tool;

import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationPreview;
import com.calio.calendar.event.service.dto.CalendarFreeTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CalendarAgentToolResultCollector {

    private final Set<EventResponse> events = new LinkedHashSet<>();
    private final Set<CalendarFreeTime> freeTimes = new LinkedHashSet<>();
    private final List<CalendarMutationPreview> mutationPreviews = new ArrayList<>();

    public void recordEvents(List<EventResponse> events) {
        this.events.addAll(events);
    }

    public void replaceEvents(List<EventResponse> events) {
        this.events.clear();
        this.events.addAll(events);
    }

    public void recordFreeTimes(List<CalendarFreeTime> freeTimes) {
        this.freeTimes.addAll(freeTimes);
    }

    public void recordMutationPreview(CalendarMutationPreview mutationPreview) {
        mutationPreviews.add(mutationPreview);
    }

    public List<EventResponse> events() {
        return List.copyOf(events);
    }

    public List<CalendarFreeTime> freeTimes() {
        return List.copyOf(freeTimes);
    }

    public List<CalendarMutationPreview> mutationPreviews() {
        return List.copyOf(mutationPreviews);
    }
}
