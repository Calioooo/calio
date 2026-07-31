package com.calio.calendar.recurrence.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
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
import java.util.List;
import java.util.Optional;
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
    private final GoogleCalendarRecurrenceEventMappingRepository googleRecurrenceMappingRepository;

    public RecurrenceEventService(
            RecurrenceEventRepository recurrenceEventRepository,
            EventRepository eventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository,
            AccountRepository accountRepository,
            TagService tagService,
            Rfc5545RecurrenceEngine recurrenceEngine,
            GoogleCalendarRecurrenceEventMappingRepository googleRecurrenceMappingRepository
    ) {
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.eventRepository = eventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
        this.accountRepository = accountRepository;
        this.tagService = tagService;
        this.recurrenceEngine = recurrenceEngine;
        this.googleRecurrenceMappingRepository = googleRecurrenceMappingRepository;
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
        rejectExternalRecurrenceMutation(recurrenceId);
        RecurrenceSchedule schedule = createSchedule(request);
        List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());

        recurrenceEvent.update(request.title(), request.description(), schedule, recurrenceRules, tag);
        recurrenceEventRepository.flush();
        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    @Transactional
    public EventResponse updateRecurrenceOccurrence(
            Long accountId,
            Long recurrenceId,
            UpdateRecurrenceOccurrenceRequest request
    ) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEventForUpdate(accountId, recurrenceId);
        rejectExternalRecurrenceMutation(recurrenceId);
        CanonicalSchedule schedule = CanonicalSchedule.recurrenceOverride(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
        Optional<RecurrenceEventOverride> existingOverride =
                findOverrideOrRejectIneligible(recurrenceEvent, request.originStartAt());
        RecurrenceEventOverride override;
        if (existingOverride.isPresent()) {
            override = existingOverride.get();
            override.activate(request.title(), request.description(), schedule);
        } else {
            override = RecurrenceEventOverride.active(
                    recurrenceEvent,
                    request.originStartAt(),
                    request.title(),
                    request.description(),
                    schedule
            );
        }
        recurrenceEventOverrideRepository.saveAndFlush(override);
        return EventResponse.recurrenceOverride(override);
    }

    @Transactional
    public void deleteRecurrenceEvent(Long accountId, Long recurrenceId) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEventForUpdate(accountId, recurrenceId);
        rejectExternalRecurrenceMutation(recurrenceId);
        recurrenceEventOverrideRepository.deleteByRecurrenceEvent_Id(recurrenceId);
        eventRepository.deleteAll(
                eventRepository.findByRecurrenceIdAndAccount_IdOrderByStartAtAsc(recurrenceId, accountId)
        );
        recurrenceEventRepository.delete(recurrenceEvent);
    }

    @Transactional
    public void deleteRecurrenceOccurrence(Long accountId, Long recurrenceId, Instant originStartAt) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEventForUpdate(accountId, recurrenceId);
        rejectExternalRecurrenceMutation(recurrenceId);
        Optional<RecurrenceEventOverride> existingOverride =
                findOverrideOrRejectIneligible(recurrenceEvent, originStartAt);
        Instant deletedAt = Instant.now();
        RecurrenceEventOverride override;
        if (existingOverride.isPresent()) {
            override = existingOverride.get();
            override.markDeleted(deletedAt);
        } else {
            override = RecurrenceEventOverride.deleted(recurrenceEvent, originStartAt, deletedAt);
        }
        recurrenceEventOverrideRepository.saveAndFlush(override);
    }

    private RecurrenceSchedule createSchedule(CreateRecurrenceEventRequest request) {
        return RecurrenceSchedule.create(
                request.allDay(),
                request.firstOccurrenceStartAt(),
                request.firstOccurrenceEndAt(),
                request.timeZone()
        );
    }

    private RecurrenceSchedule createSchedule(UpdateRecurrenceEventRequest request) {
        return RecurrenceSchedule.create(
                request.allDay(),
                request.firstOccurrenceStartAt(),
                request.firstOccurrenceEndAt(),
                request.timeZone()
        );
    }

    private Optional<RecurrenceEventOverride> findOverrideOrRejectIneligible(
            RecurrenceEvent recurrenceEvent,
            Instant originStartAt
    ) {
        Optional<RecurrenceEventOverride> existingOverride = recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAt(recurrenceEvent.getId(), originStartAt);
        if (existingOverride.isEmpty() && !recurrenceContainsOrigin(recurrenceEvent, originStartAt)) {
            throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
        }
        return existingOverride;
    }

    private boolean recurrenceContainsOrigin(RecurrenceEvent recurrenceEvent, Instant originStartAt) {
        return recurrenceEngine.containsOrigin(
                RecurrenceSchedule.from(recurrenceEvent),
                recurrenceEvent.getRecurrenceRules(),
                originStartAt
        );
    }

    private RecurrenceEvent findRecurrenceEvent(Long accountId, Long recurrenceId) {
        return recurrenceEventRepository.findByIdAndAccount_Id(recurrenceId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }

    private RecurrenceEvent findRecurrenceEventForUpdate(Long accountId, Long recurrenceId) {
        return recurrenceEventRepository.findByIdAndAccountIdForUpdate(recurrenceId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }

    private void rejectExternalRecurrenceMutation(Long recurrenceId) {
        if (googleRecurrenceMappingRepository.findByRecurrenceEvent_Id(recurrenceId).isPresent()) {
            throw new CalioException(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED);
        }
    }
}
