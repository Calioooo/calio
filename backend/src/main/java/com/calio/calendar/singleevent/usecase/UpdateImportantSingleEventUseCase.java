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
public class UpdateImportantSingleEventUseCase {

  private final SingleEventRepository eventRepository;
  private final TagRepository tagRepository;

  public UpdateImportantSingleEventUseCase(
      SingleEventRepository eventRepository, TagRepository tagRepository) {
    this.eventRepository = eventRepository;
    this.tagRepository = tagRepository;
  }

  @Transactional
  public EventResponse update(Long accountId, Long eventId, boolean importantEvent) {
    SingleEvent event =
        eventRepository
            .findByIdAndAccountIdForUpdate(eventId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    event.changeImportantEvent(importantEvent);
    eventRepository.flush();
    return EventResponse.from(event, getPersonalTag(accountId, event.getTagId()));
  }

  private Tag getPersonalTag(Long accountId, Long tagId) {
    return tagRepository
        .findPersonalDefaultTagById(tagId)
        .or(() -> tagRepository.findPersonalCustomTagById(accountId, tagId))
        .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
  }
}
