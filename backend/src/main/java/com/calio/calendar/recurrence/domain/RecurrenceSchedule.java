package com.calio.calendar.recurrence.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Set;
import net.fortuna.ical4j.model.TimeZoneRegistry;

public record RecurrenceSchedule(
        LocalDate startDate,
        LocalDate endDate,
        LocalTime startTime,
        LocalTime endTime,
        boolean allDay,
        String timeZone
) {

    private static final Set<String> IANA_TIME_ZONES = ZoneId.getAvailableZoneIds();

    public static RecurrenceSchedule create(
            Boolean allDay,
            LocalDate startDate,
            LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime,
            String timeZone
    ) {
        if (Boolean.TRUE.equals(allDay)) {
            return createAllDay(startDate, endDate, startTime, endTime, timeZone);
        }
        return createTimed(startDate, endDate, startTime, endTime, timeZone);
    }

    public static RecurrenceSchedule from(RecurrenceEvent recurrenceEvent) {
        return new RecurrenceSchedule(
                recurrenceEvent.getStartDate(),
                recurrenceEvent.getEndDate(),
                recurrenceEvent.getStartTime(),
                recurrenceEvent.getEndTime(),
                recurrenceEvent.isAllDay(),
                recurrenceEvent.getTimeZone()
        );
    }

    public ZoneId zoneId() {
        return allDay ? ZoneOffset.UTC : TimeZoneRegistry.getGlobalZoneId(timeZone);
    }

    private static RecurrenceSchedule createAllDay(
            LocalDate startDate,
            LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime,
            String timeZone
    ) {
        requireDates(startDate, endDate);
        boolean hasInvalidTimeFields = startTime != null || endTime != null || timeZone != null;
        if (hasInvalidTimeFields || !startDate.isBefore(endDate)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        return new RecurrenceSchedule(
                startDate,
                endDate,
                null,
                null,
                true,
                null
        );
    }

    private static RecurrenceSchedule createTimed(
            LocalDate startDate,
            LocalDate endDate,
            LocalTime startTime,
            LocalTime endTime,
            String timeZone
    ) {
        requireDates(startDate, endDate);
        if (startTime == null || endTime == null || timeZone == null || timeZone.isBlank()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        if (startDate.isAfter(endDate) || startTime.equals(endTime)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        ZoneId zoneId = parseIanaTimeZone(timeZone);
        validateExistingScheduleTime(startDate.atTime(startTime), zoneId);
        LocalDate firstEndDate = endTime.isAfter(startTime) ? startDate : startDate.plusDays(1);
        validateExistingScheduleTime(firstEndDate.atTime(endTime), zoneId);
        return new RecurrenceSchedule(
                startDate,
                endDate,
                startTime,
                endTime,
                false,
                timeZone
        );
    }

    private static void requireDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
    }

    private static ZoneId parseIanaTimeZone(String timeZone) {
        if (!IANA_TIME_ZONES.contains(timeZone)) {
            throw new CalioException(ErrorCode.INVALID_TIME_ZONE);
        }
        return TimeZoneRegistry.getGlobalZoneId(timeZone);
    }

    private static void validateExistingScheduleTime(LocalDateTime localDateTime, ZoneId zoneId) {
        ZoneRules rules = zoneId.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(localDateTime);
        if (offsets.isEmpty()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
    }
}
