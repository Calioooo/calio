package com.calio.calendar.singleevent.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetSingleEventUseCase {

  private final SingleEventRepository eventRepository;
  private final TagRepository tagRepository;

  public GetSingleEventUseCase(SingleEventRepository eventRepository, TagRepository tagRepository) {
    this.eventRepository = eventRepository;
    this.tagRepository = tagRepository;
  }

  @Transactional(readOnly = true)
  public EventResponse get(Long accountId, Long eventId) {
    SingleEvent event =
        eventRepository
            .findByIdAndAccountId(eventId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    return EventResponse.from(event, getPersonalTag(accountId, event.getTagId()));
  }

  private Tag getPersonalTag(Long accountId, Long tagId) {
    return tagRepository
        .findPersonalDefaultTagById(tagId)
        .or(() -> tagRepository.findPersonalCustomTagById(accountId, tagId))
        .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
  }
}
