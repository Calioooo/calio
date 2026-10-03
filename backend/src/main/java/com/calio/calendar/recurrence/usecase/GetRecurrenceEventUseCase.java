package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetRecurrenceEventUseCase {

  private final RecurrenceEventRepository recurrenceEventRepository;
  private final TagRepository tagRepository;

  public GetRecurrenceEventUseCase(
      RecurrenceEventRepository recurrenceEventRepository, TagRepository tagRepository) {
    this.recurrenceEventRepository = recurrenceEventRepository;
    this.tagRepository = tagRepository;
  }

  @Transactional(readOnly = true)
  public RecurrenceEventResponse get(Long accountId, Long recurrenceId) {
    RecurrenceEvent recurrenceEvent =
        recurrenceEventRepository
            .findByIdAndAccountId(recurrenceId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    Tag tag =
        tagRepository
            .findById(recurrenceEvent.getTagId())
            .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    return RecurrenceEventResponse.from(recurrenceEvent, tag);
  }
}
