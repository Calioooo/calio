package com.calio.calendar.aicalendar.service.tool.dto;

import java.util.List;

public record CalendarFreeTime(
        String start,
        String end,
        List<String> allDayNotices
) {
}
