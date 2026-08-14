package com.calio.calendar.integration.sync.page;

import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.integration.sync.GoogleCalendarEventRequestService;
import com.calio.calendar.integration.sync.GoogleCalendarSyncRunContext;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarRecurrenceEventLookup;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarRecurrenceEventLookup.FoundRecurrenceEvent;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarRecurrenceEventLookup.MissingRecurrenceEvent;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarRecurrenceEventLoader {

    private final GoogleCalendarEventRequestService eventRequestService;
    private final GoogleCalendarRecurrenceMapper recurrenceMapper;

    public GoogleCalendarRecurrenceEventLoader(
            GoogleCalendarEventRequestService eventRequestService,
            GoogleCalendarRecurrenceMapper recurrenceMapper
    ) {
        this.eventRequestService = eventRequestService;
        this.recurrenceMapper = recurrenceMapper;
    }

    public Optional<RecurrenceEventUpsert> loadRecurrenceEvent(
            Long integrationId,
            String recurrenceEventExternalId,
            GoogleCalendarSyncRunContext context
    ) {
        GoogleCalendarRecurrenceEventLookup cached =
                context.recurrenceEventLookup(recurrenceEventExternalId);
        if (cached instanceof FoundRecurrenceEvent found) {
            return Optional.of(found.recurrenceEvent());
        }
        if (cached == MissingRecurrenceEvent.INSTANCE) {
            return Optional.empty();
        }
        return requestAndRemember(integrationId, recurrenceEventExternalId, context);
    }

    private Optional<RecurrenceEventUpsert> requestAndRemember(
            Long integrationId,
            String recurrenceEventExternalId,
            GoogleCalendarSyncRunContext context
    ) {
        Optional<GoogleCalendarEventResponse> response = eventRequestService.getEvent(
                integrationId,
                recurrenceEventExternalId,
                context
        );
        if (response.isEmpty()) {
            context.rememberRecurrenceEvent(
                    recurrenceEventExternalId,
                    MissingRecurrenceEvent.INSTANCE
            );
            return Optional.empty();
        }
        RecurrenceEventUpsert recurrenceEvent = recurrenceMapper.mapRecurrenceEvent(response.get());
        context.rememberRecurrenceEvent(
                recurrenceEventExternalId,
                new FoundRecurrenceEvent(recurrenceEvent)
        );
        return Optional.of(recurrenceEvent);
    }
}
