package com.calio.calendar.external.google;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTime;
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
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GoogleCalendarEventTimeNormalizer {

    private static final Set<String> IANA_TIME_ZONES = ZoneId.getAvailableZoneIds();

    public NormalizedEventTime normalize(GoogleCalendarEventTime eventTime) {
        if (eventTime == null || !hasExactlyOneTimeValue(eventTime)) {
            throw invalidResponse(null);
        }
        try {
            if (hasText(eventTime.date())) {
                return normalizeAllDay(eventTime.date());
            }
            return normalizeTimed(eventTime);
        } catch (DateTimeException exception) {
            throw invalidResponse(exception);
        }
    }

    public NormalizedEventSchedule normalizeSchedule(
            GoogleCalendarEventTime start,
            GoogleCalendarEventTime end
    ) {
        NormalizedEventTime normalizedStart = normalize(start);
        NormalizedEventTime normalizedEnd = normalize(end);
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

    private NormalizedEventTime normalizeTimed(GoogleCalendarEventTime eventTime) {
        ZoneId zoneId = eventTime.timeZone() == null
                ? null
                : parseIanaTimeZone(eventTime.timeZone());
        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(
                    eventTime.dateTime(),
                    DateTimeFormatter.ISO_DATE_TIME
            );
            validateSuppliedOffset(offsetDateTime, zoneId);
            return new NormalizedEventTime(
                    offsetDateTime.toInstant(),
                    false,
                    eventTime.timeZone()
            );
        } catch (java.time.format.DateTimeParseException ignored) {
            return normalizeOffsetless(eventTime.dateTime(), eventTime.timeZone(), zoneId);
        }
    }

    private NormalizedEventTime normalizeOffsetless(
            String dateTime,
            String timeZone,
            ZoneId zoneId
    ) {
        if (zoneId == null) {
            throw invalidResponse(null);
        }
        LocalDateTime localDateTime = LocalDateTime.parse(
                dateTime,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
        );
        List<ZoneOffset> validOffsets = zoneId.getRules().getValidOffsets(localDateTime);
        if (validOffsets.isEmpty()) {
            throw invalidResponse(null);
        }
        return new NormalizedEventTime(
                localDateTime.toInstant(validOffsets.getFirst()),
                false,
                timeZone
        );
    }

    private ZoneId parseIanaTimeZone(String timeZone) {
        if (!IANA_TIME_ZONES.contains(timeZone)) {
            throw invalidResponse(null);
        }
        return ZoneId.of(timeZone);
    }

    private void validateSuppliedOffset(OffsetDateTime dateTime, ZoneId zoneId) {
        if (zoneId == null) {
            return;
        }
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

    public record NormalizedEventTime(
            Instant instant,
            boolean allDay,
            String timeZone
    ) {
    }

    public record NormalizedEventSchedule(
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone
    ) {
    }
}
