package com.calio.calendar.event.service.dto;

import java.util.List;

public record CalendarFreeTime(
        String start,
        String end,
        List<String> allDayNotices
) {
}
