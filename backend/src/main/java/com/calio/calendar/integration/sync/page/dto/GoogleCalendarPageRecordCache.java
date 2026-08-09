package com.calio.calendar.integration.sync.page.dto;

import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import java.time.Instant;
import java.util.Map;

/**
 * Local records indexed for one normalized Google Calendar page.
 */
public record GoogleCalendarPageRecordCache(
        Map<String, GoogleCalendarEventMapping> eventMappings,
        Map<String, GoogleCalendarRecurrenceEventMapping> recurrenceEventMappings,
        Map<GoogleCalendarRecurrenceOverrideKey, GoogleCalendarRecurrenceOverrideMapping>
                googleOverrideMappings,
        Map<RecurrenceEventOverrideKey, RecurrenceEventOverride> recurrenceEventOverrides
) {

    public record GoogleCalendarRecurrenceOverrideKey(
            Long recurrenceEventMappingId,
            String overrideExternalEventId
    ) {
    }

    public record RecurrenceEventOverrideKey(Long recurrenceEventId, Instant originStartAt) {
    }
}
