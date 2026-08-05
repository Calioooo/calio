package com.calio.calendar.integration.service;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.integration.domain.GoogleContentHash;
import com.calio.calendar.integration.domain.GoogleCalendarItemSnapshot;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.ActiveRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import java.time.Instant;
import java.util.List;

public final class GoogleCalendarContentHasher {

    private GoogleCalendarContentHasher() {
    }

    public static GoogleCalendarItemSnapshot snapshotForEvent(EventUpsert item) {
        return new GoogleCalendarItemSnapshot(
                item.googleEtag(), item.googleUpdatedAt(), hashEvent(item)
        );
    }

    public static GoogleCalendarItemSnapshot snapshotForRecurrenceEvent(
            RecurrenceEventUpsert item
    ) {
        return new GoogleCalendarItemSnapshot(
                item.googleEtag(), item.googleUpdatedAt(), hashRecurrenceEvent(item)
        );
    }

    public static GoogleCalendarItemSnapshot snapshotForRecurrenceOverride(
            RecurrenceEventOverrideUpsert item
    ) {
        return new GoogleCalendarItemSnapshot(
                item.googleEtag(), item.googleUpdatedAt(), hashRecurrenceOverride(item)
        );
    }

    public static String hashEvent(EventUpsert item) {
        return hashEvent(item.title(), item.description(), item.schedule());
    }

    public static String hashEvent(Event event) {
        return hashSchedule("GENERAL_EVENT", event.getTitle(), event.getDescription(),
                event.getStartAt(), event.getEndAt(), event.isAllDay(), event.getTimeZone());
    }

    public static String hashRecurrenceEvent(RecurrenceEventUpsert item) {
        return hashRecurrenceEvent(item.title(), item.description(), item.schedule(),
                item.recurrenceRules());
    }

    public static String hashRecurrenceEvent(RecurrenceEvent event) {
        return GoogleContentHash.digest("RECURRENCE_MASTER", event.getRecurrenceTitle(),
                event.getRecurrenceDescription(), event.getFirstOccurrenceStartAt(),
                event.getFirstOccurrenceEndAt(), event.isAllDay(), event.getTimeZone(),
                encodeRecurrenceRules(event.getRecurrenceRules()));
    }

    public static String hashRecurrenceOverride(RecurrenceEventOverrideUpsert item) {
        if (item instanceof ActiveRecurrenceEventOverrideUpsert active) {
            return hashActiveOverride(active.recurrenceEventExternalId(), active.originStartAt(),
                    active.title(), active.description(), active.schedule());
        }
        return hashDeletedOverride(item.recurrenceEventExternalId(), item.originStartAt());
    }

    public static String hashRecurrenceOverride(
            String recurrenceEventExternalId,
            RecurrenceEventOverride override
    ) {
        if (override.isDeleted()) {
            return hashDeletedOverride(
                    recurrenceEventExternalId, override.getOriginStartAt()
            );
        }
        return hashSchedule("RECURRENCE_OVERRIDE_ACTIVE", recurrenceEventExternalId,
                override.getOriginStartAt(), override.getOverrideTitle(),
                override.getOverrideDescription(), override.getOverrideStartAt(),
                override.getOverrideEndAt(), override.isOverrideAllDay(),
                override.getOverrideTimeZone());
    }

    private static String hashEvent(
            String title,
            String description,
            NormalizedEventSchedule schedule
    ) {
        return hashSchedule("GENERAL_EVENT", title, description, schedule.startAt(),
                schedule.endAt(), schedule.allDay(), schedule.timeZone());
    }

    private static String hashRecurrenceEvent(
            String title,
            String description,
            NormalizedEventSchedule schedule,
            List<String> recurrenceRules
    ) {
        return GoogleContentHash.digest("RECURRENCE_MASTER", title, description,
                schedule.startAt(), schedule.endAt(), schedule.allDay(), schedule.timeZone(),
                encodeRecurrenceRules(recurrenceRules));
    }

    private static String hashActiveOverride(
            String recurrenceEventExternalId,
            Instant originStartAt,
            String title,
            String description,
            NormalizedEventSchedule schedule
    ) {
        return hashSchedule("RECURRENCE_OVERRIDE_ACTIVE", recurrenceEventExternalId,
                originStartAt,
                title, description, schedule.startAt(), schedule.endAt(), schedule.allDay(),
                schedule.timeZone());
    }

    private static String hashDeletedOverride(
            String recurrenceEventExternalId,
            Instant originStartAt
    ) {
        return GoogleContentHash.digest(
                "RECURRENCE_OVERRIDE_DELETED", recurrenceEventExternalId, originStartAt
        );
    }

    private static String hashSchedule(String type, Object... fields) {
        return GoogleContentHash.digest(type, fields);
    }

    private static String encodeRecurrenceRules(List<String> recurrenceRules) {
        StringBuilder encodedRules = new StringBuilder();
        for (String rule : recurrenceRules) {
            encodedRules.append(rule.length()).append(':').append(rule);
        }
        return encodedRules.toString();
    }
}
