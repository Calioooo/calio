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
            Instant googleUpdatedAt,
            String title,
            String description,
            NormalizedEventSchedule schedule
    ) implements NormalizedItem {

        @Deprecated
        public EventUpsert(
                String externalEventId,
                String ignoredProviderEtag,
                Instant googleUpdatedAt,
                String title,
                String description,
                NormalizedEventSchedule schedule
        ) {
            this(externalEventId, googleUpdatedAt, title, description, schedule);
        }
    }

    public record EventCancellation(String externalEventId) implements NormalizedItem {
    }

    public record RecurrenceEventUpsert(
            String externalEventId,
            Instant googleUpdatedAt,
            String title,
            String description,
            NormalizedEventSchedule schedule,
            List<String> recurrenceRules
    ) implements NormalizedItem {

        public RecurrenceEventUpsert {
            recurrenceRules = List.copyOf(recurrenceRules);
        }

        @Deprecated
        public RecurrenceEventUpsert(
                String externalEventId,
                String ignoredProviderEtag,
                Instant googleUpdatedAt,
                String title,
                String description,
                NormalizedEventSchedule schedule,
                List<String> recurrenceRules
        ) {
            this(externalEventId, googleUpdatedAt, title, description, schedule, recurrenceRules);
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

        Instant googleUpdatedAt();
    }

    public record ActiveRecurrenceEventOverrideUpsert(
            String externalEventId,
            String recurrenceEventExternalId,
            Instant originStartAt,
            Instant googleUpdatedAt,
            String title,
            String description,
            NormalizedEventSchedule schedule
    ) implements RecurrenceEventOverrideUpsert {

        @Deprecated
        public ActiveRecurrenceEventOverrideUpsert(
                String externalEventId,
                String recurrenceEventExternalId,
                Instant originStartAt,
                String ignoredProviderEtag,
                Instant googleUpdatedAt,
                String title,
                String description,
                NormalizedEventSchedule schedule
        ) {
            this(externalEventId, recurrenceEventExternalId, originStartAt,
                    googleUpdatedAt, title, description, schedule);
        }
    }

    public record CancelledRecurrenceEventOverrideUpsert(
            String externalEventId,
            String recurrenceEventExternalId,
            Instant originStartAt,
            Instant googleUpdatedAt
    ) implements RecurrenceEventOverrideUpsert {

        @Deprecated
        public CancelledRecurrenceEventOverrideUpsert(
                String externalEventId,
                String recurrenceEventExternalId,
                Instant originStartAt,
                String ignoredProviderEtag,
                Instant googleUpdatedAt
        ) {
            this(externalEventId, recurrenceEventExternalId, originStartAt, googleUpdatedAt);
        }
    }
}
