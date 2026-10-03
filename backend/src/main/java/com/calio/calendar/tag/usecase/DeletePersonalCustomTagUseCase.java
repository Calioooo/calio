package com.calio.calendar.tag.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeletePersonalCustomTagUseCase {

  private final SingleEventRepository singleEventRepository;
  private final RecurrenceEventRepository recurrenceEventRepository;
  private final TagRepository tagRepository;

  public DeletePersonalCustomTagUseCase(
      SingleEventRepository singleEventRepository,
      RecurrenceEventRepository recurrenceEventRepository,
      TagRepository tagRepository) {
    this.singleEventRepository = singleEventRepository;
    this.recurrenceEventRepository = recurrenceEventRepository;
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
    recurrenceEventRepository.reassignAllByTagAndAccountId(
        tag.getId(), fallbackTag.getId(), accountId);
    tagRepository.delete(tag);
  }
}
