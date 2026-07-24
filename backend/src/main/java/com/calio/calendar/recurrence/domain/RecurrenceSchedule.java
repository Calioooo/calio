package com.calio.calendar.recurrence.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Set;

public record RecurrenceSchedule(
        Instant startAt,
        Instant endAt,
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
                recurrenceEvent.getStartAt(),
                recurrenceEvent.getEndAt(),
                recurrenceEvent.isAllDay(),
                recurrenceEvent.getTimeZone()
        );
    }

    public LocalDate startDate() {
        return allDay
                ? startAt.atOffset(ZoneOffset.UTC).toLocalDate()
                : startAt.atZone(zoneId()).toLocalDate();
    }

    public LocalDate endDate() {
        return allDay
                ? endAt.atOffset(ZoneOffset.UTC).toLocalDate()
                : endAt.atZone(zoneId()).toLocalDate();
    }

    public LocalTime startTime() {
        return allDay ? null : startAt.atZone(zoneId()).toLocalTime();
    }

    public LocalTime endTime() {
        return allDay ? null : endAt.atZone(zoneId()).toLocalTime();
    }

    public ZoneId zoneId() {
        return allDay ? ZoneOffset.UTC : ZoneId.of(timeZone);
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
                startDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                endDate.atStartOfDay().toInstant(ZoneOffset.UTC),
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
        ZoneId zoneId = parseIanaTimeZone(timeZone);
        Instant startAt = resolveFirstScheduleTime(startDate.atTime(startTime), zoneId);
        Instant endAt = resolveFirstScheduleTime(endDate.atTime(endTime), zoneId);
        if (!startAt.isBefore(endAt)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        return new RecurrenceSchedule(startAt, endAt, false, zoneId.getId());
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
        return ZoneId.of(timeZone);
    }

    private static Instant resolveFirstScheduleTime(LocalDateTime localDateTime, ZoneId zoneId) {
        ZoneRules rules = zoneId.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(localDateTime);
        if (offsets.isEmpty()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        return localDateTime.toInstant(offsets.getFirst());
    }
}
