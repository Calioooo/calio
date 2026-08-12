package com.calio.calendar.aicalendar.service.tool;

import com.calio.calendar.aicalendar.config.CalendarAiProperties;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

final class CalendarToolTimeRange {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final ZoneId timeZone;

    private CalendarToolTimeRange(LocalDate startDate, LocalDate endDate, ZoneId timeZone) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.timeZone = timeZone;
    }

    static CalendarToolTimeRange from(
            String startDate,
            String endDate,
            ZoneId timeZone,
            CalendarAiProperties properties
    ) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days < 1 || days > properties.getMaximumQueryDays()) {
            throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
        }
        return new CalendarToolTimeRange(start, end, timeZone);
    }

    LocalDate startDate() {
        return startDate;
    }

    LocalDate endDate() {
        return endDate;
    }

    ZoneId timeZone() {
        return timeZone;
    }
}
