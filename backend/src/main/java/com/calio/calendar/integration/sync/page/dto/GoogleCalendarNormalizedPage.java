package com.calio.calendar.integration.sync.page.dto;

import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import java.time.Instant;
import java.util.List;

public record GoogleCalendarNormalizedPage(
        List<NormalizedItem> items,
        String nextPageToken,
        String nextSyncToken
) {

    public GoogleCalendarNormalizedPage {
        items = List.copyOf(items);
    }

    public boolean hasNextPage() {
        return nextPageToken != null;
    }

    public sealed interface NormalizedItem permits EventUpsert, EventCancellation,
            RecurrenceEventUpsert, RecurrenceEventCancellation,
            RecurrenceEventOverrideUpsert {

        String externalEventId();
    }

    public record EventUpsert(
            String externalEventId,
            String title,
            String description,
            NormalizedEventSchedule schedule
    ) implements NormalizedItem {

    }

    public record EventCancellation(String externalEventId) implements NormalizedItem {
    }

    public record RecurrenceEventUpsert(
            String externalEventId,
            String title,
            String description,
            NormalizedEventSchedule schedule,
            List<String> recurrenceRules
    ) implements NormalizedItem {

        public RecurrenceEventUpsert {
            recurrenceRules = List.copyOf(recurrenceRules);
        }

    }

    public record RecurrenceEventCancellation(String externalEventId)
            implements NormalizedItem {
    }

    public sealed interface RecurrenceEventOverrideUpsert extends NormalizedItem
            permits ActiveRecurrenceEventOverrideUpsert,
            CancelledRecurrenceEventOverrideUpsert {

        String recurrenceEventExternalId();

        Instant originStartAt();

    }

    public record ActiveRecurrenceEventOverrideUpsert(
            String externalEventId,
            String recurrenceEventExternalId,
            Instant originStartAt,
            String title,
            String description,
            NormalizedEventSchedule schedule
    ) implements RecurrenceEventOverrideUpsert {

    }

    public record CancelledRecurrenceEventOverrideUpsert(
            String externalEventId,
            String recurrenceEventExternalId,
            Instant originStartAt,
            Instant deletedAt
    ) implements RecurrenceEventOverrideUpsert {

    }
}
