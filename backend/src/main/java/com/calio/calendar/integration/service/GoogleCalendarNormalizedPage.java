package com.calio.calendar.integration.service;

import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.RecurrenceEventResult;
import com.calio.calendar.integration.service.GoogleCalendarRecurrenceMapper.RecurrenceOverrideResult;
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

    public sealed interface NormalizedItem permits GeneralUpsert, GeneralCancellation,
            RecurrenceMasterUpsert, RecurrenceMasterCancellation, RecurrenceOverrideUpsert {

        String externalEventId();
    }

    public record GeneralUpsert(
            String externalEventId,
            String providerEtag,
            Instant providerUpdatedAt,
            String title,
            String description,
            NormalizedEventSchedule schedule
    ) implements NormalizedItem {
    }

    public record GeneralCancellation(String externalEventId) implements NormalizedItem {
    }

    public record RecurrenceMasterUpsert(RecurrenceEventResult result)
            implements NormalizedItem {

        @Override
        public String externalEventId() {
            return result.externalEventId();
        }
    }

    public record RecurrenceMasterCancellation(String externalEventId)
            implements NormalizedItem {
    }

    public record RecurrenceOverrideUpsert(RecurrenceOverrideResult result)
            implements NormalizedItem {

        @Override
        public String externalEventId() {
            return result.externalEventId();
        }
    }
}
