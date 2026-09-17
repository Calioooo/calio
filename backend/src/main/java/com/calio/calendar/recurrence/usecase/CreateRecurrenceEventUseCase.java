package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventChangePublisher;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateRecurrenceEventUseCase {

  private static final String FALLBACK_TAG_TITLE = "기타";

  private final AccountRepository accountRepository;
  private final TagRepository tagRepository;
  private final RecurrenceEventRepository recurrenceEventRepository;
  private final Rfc5545RecurrenceEngine recurrenceEngine;
  private final RecurrenceEventChangePublisher changePublisher;

  public CreateRecurrenceEventUseCase(
      AccountRepository accountRepository,
      TagRepository tagRepository,
      RecurrenceEventRepository recurrenceEventRepository,
      Rfc5545RecurrenceEngine recurrenceEngine,
      RecurrenceEventChangePublisher changePublisher) {
    this.accountRepository = accountRepository;
    this.tagRepository = tagRepository;
    this.recurrenceEventRepository = recurrenceEventRepository;
    this.recurrenceEngine = recurrenceEngine;
    this.changePublisher = changePublisher;
  }

  @Transactional
  public RecurrenceEventResponse create(Long accountId, CreateRecurrenceEventRequest request) {
    RecurrenceSchedule schedule =
        RecurrenceSchedule.create(
            request.allDay(),
            request.firstOccurrenceStartAt(),
            request.firstOccurrenceEndAt(),
            request.timeZone());
    List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.INTERNAL_SERVER_ERROR));
    Tag tag = findTagOrDefault(accountId, request.tagId());
    RecurrenceEvent recurrenceEvent =
        recurrenceEventRepository.saveAndFlush(
            new RecurrenceEvent(
                request.title(), request.description(), schedule, recurrenceRules, tag, account));
    changePublisher.recurrenceEventCreated(accountId, recurrenceEvent);
    return RecurrenceEventResponse.from(recurrenceEvent, true);
  }

  private Tag findTagOrDefault(Long accountId, Long tagId) {
    if (tagId == null) {
      return tagRepository
          .findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(
              TagType.PERSONAL_DEFAULT, FALLBACK_TAG_TITLE)
          .orElseThrow(() -> new CalioException(ErrorCode.DEFAULT_TAG_NOT_FOUND));
    }
    return tagRepository
        .findByIdAndTagTypeAndAccountIsNullAndGroupSpaceIsNull(tagId, TagType.PERSONAL_DEFAULT)
        .or(() -> tagRepository.findByIdAndTagTypeAndAccount_Id(tagId, TagType.CUSTOM, accountId))
        .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
  }
}
