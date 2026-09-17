package com.calio.calendar.aicalendar.service.tool;

import java.util.concurrent.atomic.AtomicInteger;

public class CalendarAgentToolCallCounter {

    private final AtomicInteger callCount = new AtomicInteger();

    public boolean incrementWithin(int maximumCalls) {
        return callCount.incrementAndGet() <= maximumCalls;
    }
}
