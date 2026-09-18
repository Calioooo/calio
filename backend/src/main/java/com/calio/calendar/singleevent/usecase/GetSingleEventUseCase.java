package com.calio.calendar.singleevent.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.tag.service.TagQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetSingleEventUseCase {

    private final SingleEventRepository eventRepository;
    private final TagQueryService tagQueryService;

    public GetSingleEventUseCase(SingleEventRepository eventRepository, TagQueryService tagQueryService) {
        this.eventRepository = eventRepository;
        this.tagQueryService = tagQueryService;
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long accountId, Long eventId) {
        SingleEvent event = eventRepository.findByIdAndAccountId(eventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
        return EventResponse.from(event, tagQueryService.getTag(accountId, event.getTagId()));
    }
}
