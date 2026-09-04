package com.calio.calendar.aicalendar.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CalendarAiMutationPolicy {

    private static final long RECURRENCE_END_DATE_MAX_YEARS_AHEAD = 100;
    private static final DateTimeFormatter UNTIL_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final Pattern SIMPLE_RECURRENCE_RULE = Pattern.compile(
            "RRULE:FREQ=(DAILY|WEEKLY|MONTHLY|YEARLY)(?:;UNTIL=(\\d{8}(?:T\\d{6}Z)?))?"
    );

    private final Clock clock;

    public CalendarAiMutationPolicy(Clock clock) {
        this.clock = clock;
    }

    public void validateCreateRecurrence(
            List<String> recurrenceRules,
            RecurrenceSchedule schedule
    ) {
        String until = requireSimpleRecurrenceRule(recurrenceRules);
        requireMinutePrecision(schedule);
        requireAllowedEndDate(until, schedule);
    }

    private String requireSimpleRecurrenceRule(List<String> recurrenceRules) {
        if (recurrenceRules == null || recurrenceRules.size() != 1) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        Matcher matcher = SIMPLE_RECURRENCE_RULE.matcher(recurrenceRules.getFirst());
        if (!matcher.matches()) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        return matcher.group(2);
    }

    private void requireMinutePrecision(RecurrenceSchedule schedule) {
        if (schedule.allDay()) {
            return;
        }
        if (!isMinuteAligned(schedule.firstOccurrenceStartAt())
                || !isMinuteAligned(schedule.firstOccurrenceEndAt())) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
    }

    private boolean isMinuteAligned(Instant instant) {
        return instant.getEpochSecond() % 60 == 0 && instant.getNano() == 0;
    }

    private void requireAllowedEndDate(String until, RecurrenceSchedule schedule) {
        if (until == null) {
            return;
        }
        LocalDate endDate = parseEndDate(until);
        if (endDate.isAfter(maximumEndDate()) || endDate.isBefore(firstOccurrenceDate(schedule))) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        requireUntilMatchesFirstOccurrenceTime(until, endDate, schedule);
    }

    private LocalDate parseEndDate(String until) {
        try {
            return LocalDate.parse(until.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE, exception);
        }
    }

    private LocalDate firstOccurrenceDate(RecurrenceSchedule schedule) {
        return schedule.firstOccurrenceLocalStart().toLocalDate();
    }

    private void requireUntilMatchesFirstOccurrenceTime(
            String until,
            LocalDate endDate,
            RecurrenceSchedule schedule
    ) {
        String expectedUntil = schedule.allDay()
                ? DateTimeFormatter.BASIC_ISO_DATE.format(endDate)
                : UNTIL_FORMATTER.withZone(ZoneOffset.UTC).format(endDate
                        .atTime(schedule.firstOccurrenceLocalStart().toLocalTime())
                        .atZone(schedule.zoneId())
                        .toInstant());
        if (!expectedUntil.equals(until)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
    }

    private LocalDate maximumEndDate() {
        return LocalDate.now(clock)
                .plusYears(RECURRENCE_END_DATE_MAX_YEARS_AHEAD)
                .withMonth(12)
                .withDayOfMonth(31);
    }
}
