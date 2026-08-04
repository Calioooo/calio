package com.calio.calendar.integration.service;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.integration.domain.GoogleContentHash;
import com.calio.calendar.integration.domain.GoogleProviderObservation;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.ActiveRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import java.time.Instant;
import java.util.List;

public final class GoogleProviderContentProjector {

    private GoogleProviderContentProjector() {
    }

    public static GoogleProviderObservation eventObservation(EventUpsert item) {
        return new GoogleProviderObservation(
                item.googleEtag(), item.googleUpdatedAt(), event(item)
        );
    }

    public static GoogleProviderObservation recurrenceEventObservation(
            RecurrenceEventUpsert item
    ) {
        return new GoogleProviderObservation(
                item.googleEtag(), item.googleUpdatedAt(), recurrenceMaster(item)
        );
    }

    public static GoogleProviderObservation recurrenceEventOverrideObservation(
            RecurrenceEventOverrideUpsert item
    ) {
        return new GoogleProviderObservation(
                item.googleEtag(), item.googleUpdatedAt(), recurrenceOverride(item)
        );
    }

    public static String event(EventUpsert item) {
        return event(item.title(), item.description(), item.schedule());
    }

    public static String event(Event event) {
        return scheduleDigest("GENERAL_EVENT", event.getTitle(), event.getDescription(),
                event.getStartAt(), event.getEndAt(), event.isAllDay(), event.getTimeZone());
    }

    public static String recurrenceMaster(RecurrenceEventUpsert item) {
        return recurrenceMaster(item.title(), item.description(), item.schedule(),
                item.recurrenceRules());
    }

    public static String recurrenceMaster(RecurrenceEvent event) {
        return GoogleContentHash.digest("RECURRENCE_MASTER", event.getRecurrenceTitle(),
                event.getRecurrenceDescription(), event.getFirstOccurrenceStartAt(),
                event.getFirstOccurrenceEndAt(), event.isAllDay(), event.getTimeZone(),
                recurrenceContent(event.getRecurrenceRules()));
    }

    public static String recurrenceOverride(RecurrenceEventOverrideUpsert item) {
        if (item instanceof ActiveRecurrenceEventOverrideUpsert active) {
            return overrideActive(active.recurrenceEventExternalId(), active.originStartAt(),
                    active.title(), active.description(), active.schedule());
        }
        return overrideDeleted(item.recurrenceEventExternalId(), item.originStartAt());
    }

    public static String recurrenceOverride(
            String externalMasterId,
            RecurrenceEventOverride override
    ) {
        if (override.isDeleted()) {
            return overrideDeleted(externalMasterId, override.getOriginStartAt());
        }
        return scheduleDigest("RECURRENCE_OVERRIDE_ACTIVE", externalMasterId,
                override.getOriginStartAt(), override.getOverrideTitle(),
                override.getOverrideDescription(), override.getOverrideStartAt(),
                override.getOverrideEndAt(), override.isOverrideAllDay(),
                override.getOverrideTimeZone());
    }

    private static String event(
            String title,
            String description,
            NormalizedEventSchedule schedule
    ) {
        return scheduleDigest("GENERAL_EVENT", title, description, schedule.startAt(),
                schedule.endAt(), schedule.allDay(), schedule.timeZone());
    }

    private static String recurrenceMaster(
            String title,
            String description,
            NormalizedEventSchedule schedule,
            List<String> recurrenceRules
    ) {
        return GoogleContentHash.digest("RECURRENCE_MASTER", title, description,
                schedule.startAt(), schedule.endAt(), schedule.allDay(), schedule.timeZone(),
                recurrenceContent(recurrenceRules));
    }

    private static String overrideActive(
            String externalMasterId,
            Instant originStartAt,
            String title,
            String description,
            NormalizedEventSchedule schedule
    ) {
        return scheduleDigest("RECURRENCE_OVERRIDE_ACTIVE", externalMasterId, originStartAt,
                title, description, schedule.startAt(), schedule.endAt(), schedule.allDay(),
                schedule.timeZone());
    }

    private static String overrideDeleted(String externalMasterId, Instant originStartAt) {
        return GoogleContentHash.digest(
                "RECURRENCE_OVERRIDE_DELETED", externalMasterId, originStartAt
        );
    }

    private static String scheduleDigest(String type, Object... fields) {
        return GoogleContentHash.digest(type, fields);
    }

    private static String recurrenceContent(List<String> recurrenceRules) {
        StringBuilder canonical = new StringBuilder();
        for (String rule : recurrenceRules) {
            canonical.append(rule.length()).append(':').append(rule);
        }
        return canonical.toString();
    }
}
