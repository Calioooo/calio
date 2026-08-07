package com.calio.calendar.external.google;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.common.time.IanaTimeZones;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTime;
import com.calio.calendar.external.google.service.dto.NormalizedEventTime;
import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.zone.ZoneRules;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GoogleCalendarEventTimeNormalizer {

    public NormalizedEventTime normalize(GoogleCalendarEventTime eventTime) {
        return normalize(eventTime, null, false);
    }

    private NormalizedEventTime normalize(
            GoogleCalendarEventTime eventTime,
            String fallbackTimeZone,
            boolean allowsFallback
    ) {
        if (eventTime == null || !hasExactlyOneTimeValue(eventTime)) {
            throw invalidResponse(null);
        }
        try {
            if (hasText(eventTime.date())) {
                return normalizeAllDay(eventTime.date());
            }
            return normalizeTimed(eventTime, fallbackTimeZone, allowsFallback);
        } catch (DateTimeException exception) {
            throw invalidResponse(exception);
        }
    }

    public NormalizedEventSchedule normalizeSchedule(
            GoogleCalendarEventTime start,
            GoogleCalendarEventTime end
    ) {
        return normalizeSchedule(start, end, null, false);
    }

    public NormalizedEventSchedule normalizeSchedule(
            GoogleCalendarEventTime start,
            GoogleCalendarEventTime end,
            String fallbackTimeZone
    ) {
        return normalizeSchedule(start, end, fallbackTimeZone, true);
    }

    private NormalizedEventSchedule normalizeSchedule(
            GoogleCalendarEventTime start,
            GoogleCalendarEventTime end,
            String fallbackTimeZone,
            boolean allowsFallback
    ) {
        NormalizedEventTime normalizedStart = normalize(start, fallbackTimeZone, allowsFallback);
        NormalizedEventTime normalizedEnd = normalize(end, fallbackTimeZone, allowsFallback);
        validateScheduleShape(normalizedStart, normalizedEnd);
        if (!normalizedStart.instant().isBefore(normalizedEnd.instant())) {
            throw invalidResponse(null);
        }
        return new NormalizedEventSchedule(
                normalizedStart.instant(),
                normalizedEnd.instant(),
                normalizedStart.allDay(),
                normalizedStart.timeZone()
        );
    }

    private NormalizedEventTime normalizeAllDay(String date) {
        Instant instant = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
        return new NormalizedEventTime(instant, true, null);
    }

    private NormalizedEventTime normalizeTimed(
            GoogleCalendarEventTime eventTime,
            String fallbackTimeZone,
            boolean allowsFallback
    ) {
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(
                    eventTime.dateTime(),
                    DateTimeFormatter.ISO_DATE_TIME
            );
            ResolvedTimeZone resolvedTimeZone =
                    resolveTimeZone(eventTime.timeZone(), fallbackTimeZone, allowsFallback);
            if (resolvedTimeZone != null) {
                validateSuppliedOffset(offsetDateTime, resolvedTimeZone.zoneId());
            }
            return new NormalizedEventTime(
                    offsetDateTime.toInstant(),
                    false,
                    resolvedTimeZone == null ? null : resolvedTimeZone.id()
            );
        } catch (java.time.format.DateTimeParseException ignored) {
            ResolvedTimeZone resolvedTimeZone =
                    resolveTimeZone(eventTime.timeZone(), fallbackTimeZone, allowsFallback);
            if (resolvedTimeZone == null) {
                throw invalidResponse(null);
            }
            return normalizeOffsetless(eventTime.dateTime(), resolvedTimeZone);
        }
    }

    private NormalizedEventTime normalizeOffsetless(
            String dateTime,
            ResolvedTimeZone resolvedTimeZone
    ) {
        LocalDateTime localDateTime = LocalDateTime.parse(
                dateTime,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );
        List<ZoneOffset> validOffsets =
                resolvedTimeZone.zoneId().getRules().getValidOffsets(localDateTime);
        if (validOffsets.isEmpty()) {
            throw invalidResponse(null);
        }
        return new NormalizedEventTime(
                localDateTime.toInstant(validOffsets.getFirst()),
                false,
                resolvedTimeZone.id()
        );
    }

    private ResolvedTimeZone resolveTimeZone(
            String explicitTimeZone,
            String fallbackTimeZone,
            boolean allowsFallback
    ) {
        if (explicitTimeZone != null) {
            return new ResolvedTimeZone(explicitTimeZone, parseIanaTimeZone(explicitTimeZone));
        }
        if (allowsFallback && fallbackTimeZone != null) {
            return new ResolvedTimeZone(fallbackTimeZone, parseIanaTimeZone(fallbackTimeZone));
        }
        return null;
    }

    private ZoneId parseIanaTimeZone(String timeZone) {
        if (!IanaTimeZones.contains(timeZone)) {
            throw invalidResponse(null);
        }
        return ZoneId.of(timeZone);
    }

    private void validateSuppliedOffset(OffsetDateTime dateTime, ZoneId zoneId) {
        ZoneRules zoneRules = zoneId.getRules();
        if (!zoneRules.getValidOffsets(dateTime.toLocalDateTime()).contains(dateTime.getOffset())) {
            throw invalidResponse(null);
        }
    }

    private void validateScheduleShape(
            NormalizedEventTime start,
            NormalizedEventTime end
    ) {
        if (start.allDay() != end.allDay()) {
            throw invalidResponse(null);
        }
        if (start.allDay()) {
            return;
        }
        if (start.timeZone() == null || !start.timeZone().equals(end.timeZone())) {
            throw invalidResponse(null);
        }
    }

    private boolean hasExactlyOneTimeValue(GoogleCalendarEventTime eventTime) {
        return hasText(eventTime.date()) ^ hasText(eventTime.dateTime());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private CalioException invalidResponse(Exception cause) {
        return cause == null
                ? new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID)
                : new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID, cause);
    }

    private record ResolvedTimeZone(String id, ZoneId zoneId) {
    }
}
