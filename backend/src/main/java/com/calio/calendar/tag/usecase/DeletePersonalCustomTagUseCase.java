package com.calio.calendar.tag.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.service.RecurrenceEventCommandService;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeletePersonalCustomTagUseCase {

  private final SingleEventRepository singleEventRepository;
  private final RecurrenceEventCommandService recurrenceEventCommandService;
  private final TagRepository tagRepository;

  public DeletePersonalCustomTagUseCase(
      SingleEventRepository singleEventRepository,
      RecurrenceEventCommandService recurrenceEventCommandService,
      TagRepository tagRepository) {
    this.singleEventRepository = singleEventRepository;
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
    singleEventRepository.reassignAllByTagAndAccountId(tag.getId(), fallbackTag.getId(), accountId);
    recurrenceEventCommandService.changeTagForRecurrenceEvents(accountId, tag, fallbackTag);
    tagRepository.delete(tag);
  }
}
