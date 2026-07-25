package com.calio.calendar.recurrence.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Set;
import net.fortuna.ical4j.model.TimeZoneRegistry;

public record RecurrenceSchedule(
        Instant firstOccurrenceStartAt,
        Instant firstOccurrenceEndAt,
        boolean allDay,
        String timeZone
) {

    private static final Set<String> IANA_TIME_ZONES = ZoneId.getAvailableZoneIds();

    public static RecurrenceSchedule create(
            Boolean allDay,
            Instant firstOccurrenceStartAt,
            Instant firstOccurrenceEndAt,
            String timeZone
    ) {
        requireOccurrenceRange(firstOccurrenceStartAt, firstOccurrenceEndAt);
        if (Boolean.TRUE.equals(allDay)) {
            return createAllDay(firstOccurrenceStartAt, firstOccurrenceEndAt, timeZone);
        }
        return createTimed(firstOccurrenceStartAt, firstOccurrenceEndAt, timeZone);
    }

    public static RecurrenceSchedule from(RecurrenceEvent recurrenceEvent) {
        return new RecurrenceSchedule(
                recurrenceEvent.getFirstOccurrenceStartAt(),
                recurrenceEvent.getFirstOccurrenceEndAt(),
                recurrenceEvent.isAllDay(),
                recurrenceEvent.getTimeZone()
        );
    }

    public ZoneId zoneId() {
        return allDay ? ZoneOffset.UTC : TimeZoneRegistry.getGlobalZoneId(timeZone);
    }

    public LocalDate firstOccurrenceDate() {
        return firstOccurrenceStartAt.atOffset(ZoneOffset.UTC).toLocalDate();
    }

    public LocalDateTime firstOccurrenceLocalStart() {
        return firstOccurrenceStartAt.atZone(zoneId()).toLocalDateTime();
    }

    public LocalDateTime firstOccurrenceLocalEnd() {
        return firstOccurrenceEndAt.atZone(zoneId()).toLocalDateTime();
    }

    public Duration timedWallClockDuration() {
        return Duration.between(firstOccurrenceLocalStart(), firstOccurrenceLocalEnd());
    }

    public long allDayDurationDays() {
        return Duration.between(firstOccurrenceStartAt, firstOccurrenceEndAt).toDays();
    }

    private static RecurrenceSchedule createAllDay(
            Instant firstOccurrenceStartAt,
            Instant firstOccurrenceEndAt,
            String timeZone
    ) {
        boolean usesUtcMidnight = isUtcMidnight(firstOccurrenceStartAt)
                && isUtcMidnight(firstOccurrenceEndAt);
        if (timeZone != null || !usesUtcMidnight) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        return new RecurrenceSchedule(
                firstOccurrenceStartAt,
                firstOccurrenceEndAt,
                true,
                null
        );
    }

    private static RecurrenceSchedule createTimed(
            Instant firstOccurrenceStartAt,
            Instant firstOccurrenceEndAt,
            String timeZone
    ) {
        if (timeZone == null || timeZone.isBlank()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        ZoneId zoneId = parseIanaTimeZone(timeZone);
        LocalDateTime localStart = firstOccurrenceStartAt.atZone(zoneId).toLocalDateTime();
        LocalDateTime localEnd = firstOccurrenceEndAt.atZone(zoneId).toLocalDateTime();
        if (!localStart.isBefore(localEnd)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        validateCanonicalOffset(localStart, firstOccurrenceStartAt, zoneId);
        validateCanonicalEndOffset(localEnd, firstOccurrenceEndAt, firstOccurrenceStartAt, zoneId);
        return new RecurrenceSchedule(
                firstOccurrenceStartAt,
                firstOccurrenceEndAt,
                false,
                timeZone
        );
    }

    private static void requireOccurrenceRange(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
    }

    private static ZoneId parseIanaTimeZone(String timeZone) {
        if (!IANA_TIME_ZONES.contains(timeZone)) {
            throw new CalioException(ErrorCode.INVALID_TIME_ZONE);
        }
        return TimeZoneRegistry.getGlobalZoneId(timeZone);
    }

    private static void validateCanonicalOffset(
            LocalDateTime localDateTime,
            Instant instant,
            ZoneId zoneId
    ) {
        ZoneRules rules = zoneId.getRules();
        List<ZoneOffset> offsets = rules.getValidOffsets(localDateTime);
        if (offsets.isEmpty() || !localDateTime.toInstant(offsets.getFirst()).equals(instant)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
    }

    private static void validateCanonicalEndOffset(
            LocalDateTime localEnd,
            Instant endAt,
            Instant startAt,
            ZoneId zoneId
    ) {
        List<ZoneOffset> offsets = zoneId.getRules().getValidOffsets(localEnd);
        if (offsets.isEmpty()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        ZoneOffset startOffset = zoneId.getRules().getOffset(startAt);
        ZoneOffset endOffset = offsets.stream()
                .filter(startOffset::equals)
                .findFirst()
                .orElse(offsets.getFirst());
        if (!localEnd.toInstant(endOffset).equals(endAt)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
    }

    private static boolean isUtcMidnight(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalTime().equals(java.time.LocalTime.MIDNIGHT);
    }
}
