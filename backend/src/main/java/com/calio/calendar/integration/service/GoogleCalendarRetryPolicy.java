package com.calio.calendar.integration.service;

import java.time.Duration;

public final class GoogleCalendarRetryPolicy {

    private static final Duration[] DELAYS = {
            Duration.ofMinutes(10),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(6)
    };

    private GoogleCalendarRetryPolicy() {
    }

    public static RetrySchedule next(int currentTier) {
        int nextTier = Math.min(currentTier + 1, DELAYS.length);
        return new RetrySchedule(nextTier, DELAYS[Math.min(currentTier, DELAYS.length - 1)]);
    }

    public record RetrySchedule(int tier, Duration delay) {
    }
}
