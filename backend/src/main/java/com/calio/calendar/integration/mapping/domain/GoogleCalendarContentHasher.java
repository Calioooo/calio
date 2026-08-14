package com.calio.calendar.integration.mapping.domain;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.ActiveRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

public final class GoogleCalendarContentHasher {

    private GoogleCalendarContentHasher() {
    }

    public static GoogleProviderObservation observation(EventUpsert item) {
        return new GoogleProviderObservation(item.googleEtag(), item.googleUpdatedAt(), hash(item));
    }

    public static GoogleProviderObservation observation(RecurrenceEventUpsert item) {
        return new GoogleProviderObservation(item.googleEtag(), item.googleUpdatedAt(), hash(item));
    }

    public static GoogleProviderObservation observation(RecurrenceEventOverrideUpsert item) {
        return new GoogleProviderObservation(item.googleEtag(), item.googleUpdatedAt(), hash(item));
    }

    public static String hash(EventUpsert item) {
        return digest("GENERAL_EVENT", item.title(), item.description(), scheduleFields(item.schedule()));
    }

    public static String hash(Event event) {
        return digest("GENERAL_EVENT", event.getTitle(), event.getDescription(),
                event.getStartAt(), event.getEndAt(), event.isAllDay(), event.getTimeZone());
    }

    public static String hash(RecurrenceEventUpsert item) {
        return digest("RECURRENCE_MASTER", item.title(), item.description(), scheduleFields(item.schedule()),
                recurrenceRules(item.recurrenceRules()));
    }

    public static String hash(RecurrenceEvent event) {
        return digest("RECURRENCE_MASTER", event.getTitle(), event.getDescription(),
                event.getFirstOccurrenceStartAt(), event.getFirstOccurrenceEndAt(), event.isAllDay(),
                event.getTimeZone(), recurrenceRules(event.getRecurrenceRules()));
    }

    public static String hash(RecurrenceEventOverrideUpsert item) {
        if (item instanceof ActiveRecurrenceEventOverrideUpsert active) {
            return digest("RECURRENCE_OVERRIDE_ACTIVE", active.recurrenceEventExternalId(),
                    active.originStartAt(), active.title(), active.description(), scheduleFields(active.schedule()));
        }
        return digest("RECURRENCE_OVERRIDE_DELETED", item.recurrenceEventExternalId(), item.originStartAt());
    }

    public static String hash(String recurrenceEventExternalId, RecurrenceEventOverride override) {
        if (override.isDeleted()) {
            return digest("RECURRENCE_OVERRIDE_DELETED", recurrenceEventExternalId, override.getOriginStartAt());
        }
        return digest("RECURRENCE_OVERRIDE_ACTIVE", recurrenceEventExternalId, override.getOriginStartAt(),
                override.getOverrideTitle(), override.getOverrideDescription(), override.getOverrideStartAt(),
                override.getOverrideEndAt(), override.isOverrideAllDay(), override.getOverrideTimeZone());
    }

    private static Object[] scheduleFields(NormalizedEventSchedule schedule) {
        return new Object[]{schedule.startAt(), schedule.endAt(), schedule.allDay(), schedule.timeZone()};
    }

    private static String recurrenceRules(List<String> rules) {
        StringBuilder value = new StringBuilder();
        rules.forEach(rule -> append(value, rule));
        return value.toString();
    }

    private static String digest(String type, Object... fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            append(digest, type);
            for (Object field : fields) {
                if (field instanceof Object[] nested) {
                    for (Object value : nested) {
                        append(digest, value);
                    }
                } else {
                    append(digest, field);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(MessageDigest digest, Object value) {
        String encoded = value == null ? "N" : "V" + value;
        byte[] bytes = encoded.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private static void append(StringBuilder builder, String value) {
        builder.append(value.length()).append(':').append(value);
    }
}
