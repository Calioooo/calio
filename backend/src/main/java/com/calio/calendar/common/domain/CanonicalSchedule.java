package com.calio.calendar.common.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

public record CanonicalSchedule(
        Instant startAt,
        Instant endAt,
        boolean allDay,
        String timeZone
) {

    private static final Set<String> IANA_TIME_ZONES = ZoneId.getAvailableZoneIds();

    public static CanonicalSchedule event(
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone
    ) {
        return create(
                startAt,
                endAt,
                allDay,
                timeZone,
                ErrorCode.INVALID_TIME_RANGE,
                ErrorCode.INVALID_ALL_DAY_SCHEDULE
        );
    }

    public static CanonicalSchedule recurrenceOverride(
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone
    ) {
        return create(
                startAt,
                endAt,
                allDay,
                timeZone,
                ErrorCode.INVALID_RECURRENCE_SCHEDULE,
                ErrorCode.INVALID_RECURRENCE_SCHEDULE
        );
    }

    private static CanonicalSchedule create(
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone,
            ErrorCode rangeError,
            ErrorCode allDayError
    ) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new CalioException(rangeError);
        }
        if (allDay) {
            validateAllDay(startAt, endAt, timeZone, allDayError);
        } else {
            validateTimeZone(timeZone);
        }
        return new CanonicalSchedule(startAt, endAt, allDay, allDay ? null : timeZone);
    }

    private static void validateAllDay(
            Instant startAt,
            Instant endAt,
            String timeZone,
            ErrorCode errorCode
    ) {
        if (timeZone != null || !isUtcMidnight(startAt) || !isUtcMidnight(endAt)) {
            throw new CalioException(errorCode);
        }
    }

    private static void validateTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank() || !IANA_TIME_ZONES.contains(timeZone)) {
            throw new CalioException(ErrorCode.INVALID_TIME_ZONE);
        }
    }

    private static boolean isUtcMidnight(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalTime().equals(LocalTime.MIDNIGHT);
    }
}
