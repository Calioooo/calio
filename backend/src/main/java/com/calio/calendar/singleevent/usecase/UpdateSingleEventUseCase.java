package com.calio.calendar.singleevent.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.controller.dto.UpdateSingleEventRequest;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.domain.SingleEventSchedule;
import com.calio.calendar.singleevent.domain.SingleEventTitle;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateSingleEventUseCase {

    private final SingleEventRepository eventRepository;
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final TagQueryService tagQueryService;

    public UpdateSingleEventUseCase(
            SingleEventRepository eventRepository,
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            TagQueryService tagQueryService
    ) {
        this.eventRepository = eventRepository;
        this.eventMappingQueryService = eventMappingQueryService;
        this.tagQueryService = tagQueryService;
    }

    @Transactional
    public EventResponse update(Long accountId, Long eventId, UpdateSingleEventRequest request) {
        SingleEvent event = eventRepository.findByIdAndAccountIdForUpdate(eventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
        rejectExternalMutation(accountId, eventId);
        Tag tag = tagQueryService.getTagOrDefault(accountId, request.tagId());
        event.replace(
                new SingleEventTitle(request.title()),
                request.description(),
                new SingleEventSchedule(request.startAt(), request.endAt(), request.allDay(), request.timeZone())
        );
        event.changeTag(tag.getId());
        eventRepository.flush();
        return EventResponse.from(event, tag);
    }

    private void rejectExternalMutation(Long accountId, Long eventId) {
        if (eventMappingQueryService.hasExternalEventMapping(eventId, accountId)) {
            throw new CalioException(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED);
        }
    }
}
