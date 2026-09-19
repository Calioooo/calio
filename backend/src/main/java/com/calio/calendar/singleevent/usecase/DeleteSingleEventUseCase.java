package com.calio.calendar.singleevent.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.sharing.event.service.PersonalEventGroupShareCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteSingleEventUseCase {

    private final SingleEventRepository eventRepository;
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final PersonalEventGroupShareCommandService eventShareCommandService;

    public DeleteSingleEventUseCase(SingleEventRepository eventRepository,
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            PersonalEventGroupShareCommandService eventShareCommandService) {
        this.eventRepository = eventRepository;
        this.eventMappingQueryService = eventMappingQueryService;
        this.eventShareCommandService = eventShareCommandService;
    }

    @Transactional
    public void delete(Long accountId, Long eventId) {
        SingleEvent event = eventRepository.findByIdAndAccountIdForUpdate(eventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
        if (eventMappingQueryService.hasExternalEventMapping(eventId, accountId)) {
            throw new CalioException(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED);
        }
        eventShareCommandService.deleteAllForSourceEvent(eventId);
        eventRepository.delete(event);
    }
}
