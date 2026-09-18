package com.calio.calendar.integration.sync.page;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.sharing.event.service.PersonalEventGroupShareCommandService;
import com.calio.calendar.tag.domain.Tag;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarEventChangeService {

    private final GoogleCalendarEventMappingCommandService eventMappingCommandService;
    private final SingleEventRepository eventRepository;
    private final PersonalEventGroupShareCommandService eventShareCommandService;

    public GoogleCalendarEventChangeService(
            GoogleCalendarEventMappingCommandService eventMappingCommandService,
            SingleEventRepository eventRepository,
            PersonalEventGroupShareCommandService eventShareCommandService
    ) {
        this.eventMappingCommandService = eventMappingCommandService;
        this.eventRepository = eventRepository;
        this.eventShareCommandService = eventShareCommandService;
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
        SingleEvent event = eventRepository.save(new SingleEvent(
                item.title(),
                item.description(),
                item.schedule().startAt(),
                item.schedule().endAt(),
                item.schedule().allDay(),
                item.schedule().timeZone(),
                defaultTag.getId(),
                account.getId()
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
        eventShareCommandService.deleteAllForSourceEvent(eventMapping.getEvent().getId());
        eventRepository.delete(eventMapping.getEvent());
    }
}
