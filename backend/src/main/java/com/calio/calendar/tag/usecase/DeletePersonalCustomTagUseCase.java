package com.calio.calendar.tag.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.recurrence.service.RecurrenceEventCommandService;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeletePersonalCustomTagUseCase {

  private final EventCommandService eventCommandService;
  private final RecurrenceEventCommandService recurrenceEventCommandService;
  private final TagRepository tagRepository;

  public DeletePersonalCustomTagUseCase(
      EventCommandService eventCommandService,
      RecurrenceEventCommandService recurrenceEventCommandService,
      TagRepository tagRepository) {
    this.eventCommandService = eventCommandService;
    this.recurrenceEventCommandService = recurrenceEventCommandService;
    this.tagRepository = tagRepository;
  }

  @Transactional
  public void delete(Long accountId, Long tagId) {
    Tag tag =
        tagRepository
            .findPersonalCustomTagById(accountId, tagId)
            .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    Tag fallbackTag =
        tagRepository
            .findPersonalFallbackTag()
            .orElseThrow(() -> new CalioException(ErrorCode.DEFAULT_TAG_NOT_FOUND));
    eventCommandService.changeTagForTargetEvents(accountId, tag, fallbackTag);
    recurrenceEventCommandService.changeTagForRecurrenceEvents(accountId, tag, fallbackTag);
    tagRepository.delete(tag);
  }
}
