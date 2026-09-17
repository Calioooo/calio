package com.calio.calendar.aicalendar.controller.dto;

import com.calio.calendar.event.service.dto.CalendarFreeTime;
import java.util.List;

public record FreeTimeResponse(
        String start,
        String end,
        List<String> allDayNotices
) {

    public static FreeTimeResponse from(CalendarFreeTime freeTime) {
        return new FreeTimeResponse(freeTime.start(), freeTime.end(), freeTime.allDayNotices());
    }
}
