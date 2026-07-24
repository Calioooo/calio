package com.calio.calendar.recurrence.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagService;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecurrenceEventService {

    private final RecurrenceEventRepository recurrenceEventRepository;
    private final EventRepository eventRepository;
    private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;
    private final AccountRepository accountRepository;
    private final TagService tagService;
    private final Rfc5545RecurrenceEngine recurrenceEngine;

    public RecurrenceEventService(
            RecurrenceEventRepository recurrenceEventRepository,
            EventRepository eventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository,
            AccountRepository accountRepository,
            TagService tagService,
            Rfc5545RecurrenceEngine recurrenceEngine
    ) {
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.eventRepository = eventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
        this.accountRepository = accountRepository;
        this.tagService = tagService;
        this.recurrenceEngine = recurrenceEngine;
    }

    @Transactional
    public RecurrenceEventResponse createRecurrenceEvent(Long accountId, CreateRecurrenceEventRequest request) {
        RecurrenceSchedule schedule = createSchedule(request);
        List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
        Account account = accountRepository.getReferenceById(accountId);
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.save(new RecurrenceEvent(
                request.title(),
                request.description(),
                schedule,
                recurrenceRules,
                tag,
                account
        ));
        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    public RecurrenceEventResponse getRecurrenceEvent(Long accountId, Long recurrenceId) {
        return RecurrenceEventResponse.from(findRecurrenceEvent(accountId, recurrenceId));
    }

    @Transactional
    public RecurrenceEventResponse updateRecurrenceEvent(
            Long accountId,
            Long recurrenceId,
            UpdateRecurrenceEventRequest request
    ) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEventForUpdate(accountId, recurrenceId);
        RecurrenceSchedule schedule = createSchedule(request);
        List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());

        recurrenceEvent.update(request.title(), request.description(), schedule, recurrenceRules, tag);
        recurrenceEventRepository.flush();
        recurrenceEventOverrideRepository.deleteByRecurrenceEvent_Id(recurrenceId);
        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    @Transactional
    public EventResponse updateRecurrenceOccurrence(
            Long accountId,
            Long recurrenceId,
            UpdateRecurrenceOccurrenceRequest request
    ) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEventForUpdate(accountId, recurrenceId);
        validateOverrideSchedule(recurrenceEvent, request.startAt(), request.endAt());
        validateOriginStartAt(recurrenceEvent, request.originStartAt());

        RecurrenceEventOverride override = recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, request.originStartAt())
                .orElseGet(() -> RecurrenceEventOverride.active(
                        recurrenceEvent,
                        request.originStartAt(),
                        request.title(),
                        request.description(),
                        request.startAt(),
                        request.endAt()
                ));
        override.activate(request.title(), request.description(), request.startAt(), request.endAt());
        recurrenceEventOverrideRepository.saveAndFlush(override);
        return EventResponse.recurrenceOverride(override);
    }

    @Transactional
    public void deleteRecurrenceEvent(Long accountId, Long recurrenceId) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEventForUpdate(accountId, recurrenceId);
        recurrenceEventOverrideRepository.deleteByRecurrenceEvent_Id(recurrenceId);
        eventRepository.deleteAll(
                eventRepository.findByRecurrenceIdAndAccount_IdOrderByStartAtAsc(recurrenceId, accountId)
        );
        recurrenceEventRepository.delete(recurrenceEvent);
    }

    @Transactional
    public void deleteRecurrenceOccurrence(Long accountId, Long recurrenceId, Instant originStartAt) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEventForUpdate(accountId, recurrenceId);
        validateOriginStartAt(recurrenceEvent, originStartAt);

        RecurrenceEventOverride override = recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, originStartAt)
                .orElseGet(() -> RecurrenceEventOverride.deleted(recurrenceEvent, originStartAt, Instant.now()));
        override.markDeleted(Instant.now());
        recurrenceEventOverrideRepository.saveAndFlush(override);
    }

    private RecurrenceSchedule createSchedule(CreateRecurrenceEventRequest request) {
        return RecurrenceSchedule.create(
                request.allDay(),
                request.startDate(),
                request.endDate(),
                request.startTime(),
                request.endTime(),
                request.timeZone()
        );
    }

    private RecurrenceSchedule createSchedule(UpdateRecurrenceEventRequest request) {
        return RecurrenceSchedule.create(
                request.allDay(),
                request.startDate(),
                request.endDate(),
                request.startTime(),
                request.endTime(),
                request.timeZone()
        );
    }

    private void validateOverrideSchedule(RecurrenceEvent recurrenceEvent, Instant startAt, Instant endAt) {
        if (!startAt.isBefore(endAt)) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
        if (!recurrenceEvent.isAllDay()) {
            return;
        }
        boolean usesUtcMidnight = isUtcMidnight(startAt) && isUtcMidnight(endAt);
        if (!usesUtcMidnight) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_SCHEDULE);
        }
    }

    private boolean isUtcMidnight(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalTime().equals(LocalTime.MIDNIGHT);
    }

    private void validateOriginStartAt(RecurrenceEvent recurrenceEvent, Instant originStartAt) {
        boolean exists = recurrenceEngine.containsOrigin(
                RecurrenceSchedule.from(recurrenceEvent),
                recurrenceEvent.getRecurrenceRules(),
                originStartAt
        );
        if (!exists) {
            throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
        }
    }

    private RecurrenceEvent findRecurrenceEvent(Long accountId, Long recurrenceId) {
        return recurrenceEventRepository.findByIdAndAccount_Id(recurrenceId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }

    private RecurrenceEvent findRecurrenceEventForUpdate(Long accountId, Long recurrenceId) {
        return recurrenceEventRepository.findByIdAndAccountIdForUpdate(recurrenceId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }
}
