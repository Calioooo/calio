package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetRecurrenceOccurrenceUseCase {

  private final RecurrenceEventRepository recurrenceEventRepository;
  private final RecurrenceEventOverrideRepository overrideRepository;
  private final TagRepository tagRepository;
  private final Rfc5545RecurrenceEngine recurrenceEngine;

  public GetRecurrenceOccurrenceUseCase(
      RecurrenceEventRepository recurrenceEventRepository,
      RecurrenceEventOverrideRepository overrideRepository,
      TagRepository tagRepository,
      Rfc5545RecurrenceEngine recurrenceEngine) {
    this.recurrenceEventRepository = recurrenceEventRepository;
    this.overrideRepository = overrideRepository;
    this.tagRepository = tagRepository;
    this.recurrenceEngine = recurrenceEngine;
  }

  @Transactional(readOnly = true)
  public EventResponse get(Long accountId, Long recurrenceId, Instant originStartAt) {
    RecurrenceEvent recurrenceEvent =
        recurrenceEventRepository
            .findByIdAndAccountId(recurrenceId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    Tag tag =
        tagRepository
            .findById(recurrenceEvent.getTagId())
            .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    Optional<RecurrenceEventOverride> override =
        overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, originStartAt);
    if (override.isPresent()) {
      if (override.get().isDeleted()) {
        throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
      }
      return EventResponse.recurrenceOverride(override.get(), tag);
    }
    RecurrenceOccurrence occurrence =
        recurrenceEngine
            .expand(
                RecurrenceSchedule.from(recurrenceEvent),
                recurrenceEvent.getRecurrenceRules(),
                originStartAt,
                originStartAt.plusNanos(1))
            .stream()
            .filter(candidate -> originStartAt.equals(candidate.originStartAt()))
            .findFirst()
            .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND));
    return EventResponse.recurrenceOccurrence(recurrenceEvent, occurrence, tag);
  }
}
