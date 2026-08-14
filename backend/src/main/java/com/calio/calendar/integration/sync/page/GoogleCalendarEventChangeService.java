package com.calio.calendar.integration.sync.page;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.tag.domain.Tag;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarEventChangeService {

    private final GoogleCalendarEventMappingCommandService eventMappingCommandService;
    private final EventCommandService eventCommandService;

    public GoogleCalendarEventChangeService(
            GoogleCalendarEventMappingCommandService eventMappingCommandService,
            EventCommandService eventCommandService
    ) {
        this.eventMappingCommandService = eventMappingCommandService;
        this.eventCommandService = eventCommandService;
    }

    public void applyUpsert(
            GoogleCalendarIntegration integration,
            EventUpsert item,
            GoogleCalendarPageRecordCache cache,
            Account account,
            Tag defaultTag
    ) {
        var eventMappings = cache.eventMappings();
        GoogleCalendarEventMapping existingMapping = eventMappings.get(item.externalEventId());
        if (existingMapping != null) {
            existingMapping.getEvent().replace(
                    item.title(),
                    item.description(),
                    item.schedule().startAt(),
                    item.schedule().endAt(),
                    item.schedule().allDay(),
                    item.schedule().timeZone()
            );
            existingMapping.updateProviderVersion(item.googleEtag(), item.googleUpdatedAt());
            return;
        }
        Event event = eventCommandService.createEvent(new Event(
                item.title(),
                item.description(),
                item.schedule().startAt(),
                item.schedule().endAt(),
                item.schedule().allDay(),
                item.schedule().timeZone(),
                null,
                defaultTag,
                account
        ));
        GoogleCalendarEventMapping mapping = eventMappingCommandService.createEventMapping(
                new GoogleCalendarEventMapping(
                        integration,
                        event,
                        item.externalEventId(),
                        item.googleEtag(),
                        item.googleUpdatedAt()
                )
        );
        eventMappings.put(item.externalEventId(), mapping);
    }

    public void applyCancellation(
            String externalEventId,
            GoogleCalendarPageRecordCache cache
    ) {
        var eventMappings = cache.eventMappings();
        GoogleCalendarEventMapping eventMapping = eventMappings.remove(externalEventId);
        if (eventMapping == null) {
            return;
        }
        eventMappingCommandService.deleteEventMapping(eventMapping);
        eventCommandService.deleteEvent(eventMapping.getEvent());
    }
}
