package com.calio.calendar.recurrence.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
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
public class UpdateRecurrenceEventUseCase {

    private static final String FALLBACK_TAG_TITLE = "기타";

    private final RecurrenceEventRepository recurrenceEventRepository;
    private final TagRepository tagRepository;
    private final Rfc5545RecurrenceEngine recurrenceEngine;
    private final RecurrenceEventChangePublisher changePublisher;

    public UpdateRecurrenceEventUseCase(
            RecurrenceEventRepository recurrenceEventRepository,
            TagRepository tagRepository,
            Rfc5545RecurrenceEngine recurrenceEngine,
            RecurrenceEventChangePublisher changePublisher
    ) {
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.tagRepository = tagRepository;
        this.recurrenceEngine = recurrenceEngine;
        this.changePublisher = changePublisher;
    }

    @Transactional
    public RecurrenceEventResponse update(
            Long accountId,
            Long recurrenceEventId,
            UpdateRecurrenceEventRequest request
    ) {
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository
                .findByIdAndAccountIdForUpdate(recurrenceEventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
        RecurrenceSchedule schedule = RecurrenceSchedule.create(
                request.allDay(),
                request.firstOccurrenceStartAt(),
                request.firstOccurrenceEndAt(),
                request.timeZone()
        );
        List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
        recurrenceEvent.update(
                request.title(),
                request.description(),
                schedule,
                recurrenceRules,
                findTagOrDefault(accountId, request.tagId())
        );
        changePublisher.recurrenceEventUpdated(accountId, recurrenceEvent);
        return RecurrenceEventResponse.from(recurrenceEvent, true);
    }

    private Tag findTagOrDefault(Long accountId, Long tagId) {
        if (tagId == null) {
            return tagRepository
                    .findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(
                            TagType.PERSONAL_DEFAULT,
                            FALLBACK_TAG_TITLE
                    )
                    .orElseThrow(() -> new CalioException(ErrorCode.DEFAULT_TAG_NOT_FOUND));
        }
        return tagRepository.findByIdAndTagTypeAndAccountIsNullAndGroupSpaceIsNull(
                        tagId,
                        TagType.PERSONAL_DEFAULT
                )
                .or(() -> tagRepository.findByIdAndTagTypeAndAccount_Id(
                        tagId,
                        TagType.CUSTOM,
                        accountId
                ))
                .orElseThrow(() -> new CalioException(ErrorCode.TAG_NOT_FOUND));
    }
}
