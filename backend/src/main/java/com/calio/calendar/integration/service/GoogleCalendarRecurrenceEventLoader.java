package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.dto.GoogleCalendarEventItem;
import com.calio.calendar.integration.service.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import com.calio.calendar.integration.service.dto.GoogleCalendarRecurrenceEventLookup;
import com.calio.calendar.integration.service.dto.GoogleCalendarRecurrenceEventLookup.FoundRecurrenceEvent;
import com.calio.calendar.integration.service.dto.GoogleCalendarRecurrenceEventLookup.MissingRecurrenceEvent;
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
        Optional<GoogleCalendarEventItem> response = eventRequestService.getEvent(
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
        RecurrenceEventUpsert recurrenceEvent = mapRecurrenceEvent(
                recurrenceEventExternalId,
                response.get()
        );
        context.rememberRecurrenceEvent(
                recurrenceEventExternalId,
                new FoundRecurrenceEvent(recurrenceEvent)
        );
        return Optional.of(recurrenceEvent);
    }

    private RecurrenceEventUpsert mapRecurrenceEvent(
            String requestedExternalId,
            GoogleCalendarEventItem response
    ) {
        boolean isInvalid = !requestedExternalId.equals(response.id())
                || response.isCancelled()
                || !response.isRecurrenceEvent()
                || response.isRecurrenceOverride();
        if (isInvalid) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
        }
        return recurrenceMapper.mapRecurrenceEvent(response);
    }
}
