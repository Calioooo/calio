package com.calio.calendar.recurrence.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.tag.service.TagQueryService;
import com.calio.calendar.sharing.recurrence.service.PersonalRecurrenceGroupShareCommandService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecurrenceEventService {

    private final RecurrenceEventQueryService recurrenceEventQueryService;
    private final RecurrenceEventCommandService recurrenceEventCommandService;
    private final AccountQueryService accountQueryService;
    private final TagQueryService tagQueryService;
    private final EventCommandService eventCommandService;
    private final Rfc5545RecurrenceEngine recurrenceEngine;
    private final Clock clock;
    private final PersonalRecurrenceGroupShareCommandService recurrenceShareCommandService;

    public RecurrenceEventService(
            RecurrenceEventQueryService recurrenceEventQueryService,
            RecurrenceEventCommandService recurrenceEventCommandService,
            AccountQueryService accountQueryService,
            TagQueryService tagQueryService,
            EventCommandService eventCommandService,
            Rfc5545RecurrenceEngine recurrenceEngine,
            Clock clock,
            PersonalRecurrenceGroupShareCommandService recurrenceShareCommandService
    ) {
        this.recurrenceEventQueryService = recurrenceEventQueryService;
        this.recurrenceEventCommandService = recurrenceEventCommandService;
        this.accountQueryService = accountQueryService;
        this.tagQueryService = tagQueryService;
        this.eventCommandService = eventCommandService;
        this.recurrenceEngine = recurrenceEngine;
        this.clock = clock;
        this.recurrenceShareCommandService = recurrenceShareCommandService;
    }

    @Transactional
    public RecurrenceEventResponse createRecurrenceEvent(Long accountId, CreateRecurrenceEventRequest request) {
        RecurrenceSchedule schedule = createSchedule(request);
        List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
        Account account = accountQueryService.getAccount(accountId);
        Tag tag = tagQueryService.getTagOrDefault(accountId, request.tagId());
        RecurrenceEvent recurrenceEvent = recurrenceEventCommandService.createRecurrenceEvent(new RecurrenceEvent(
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
        return RecurrenceEventResponse.from(
                recurrenceEventQueryService.getRecurrenceEvent(accountId, recurrenceId)
        );
    }

    @Transactional
    public RecurrenceEventResponse updateRecurrenceEvent(
            Long accountId,
            Long recurrenceId,
            UpdateRecurrenceEventRequest request
    ) {
        RecurrenceEvent recurrenceEvent = recurrenceEventCommandService
                .lockRecurrenceEvent(accountId, recurrenceId);
        RecurrenceSchedule schedule = createSchedule(request);
        List<String> recurrenceRules = recurrenceEngine.validate(schedule, request.recurrence());
        Tag tag = tagQueryService.getTagOrDefault(accountId, request.tagId());
        recurrenceEventCommandService.updateRecurrenceEvent(
                recurrenceEvent,
                request,
                schedule,
                recurrenceRules,
                tag
        );
        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    @Transactional
    public EventResponse updateRecurrenceOccurrence(
            Long accountId,
            Long recurrenceId,
            UpdateRecurrenceOccurrenceRequest request
    ) {
        RecurrenceEvent recurrenceEvent = recurrenceEventCommandService
                .lockRecurrenceEvent(accountId, recurrenceId);
        CanonicalSchedule schedule = CanonicalSchedule.recurrenceOverride(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
        Optional<RecurrenceEventOverride> existingOverride =
                findOverrideOrRejectIneligible(recurrenceEvent, request.originStartAt());
        RecurrenceEventOverride override = existingOverride.orElseGet(() -> RecurrenceEventOverride.active(
                recurrenceEvent,
                request.originStartAt(),
                request.title(),
                request.description(),
                schedule
        ));
        if (existingOverride.isPresent()) {
            override.activate(request.title(), request.description(), schedule);
        }
        recurrenceEventCommandService.createOrUpdateRecurrenceOverride(override);
        return EventResponse.recurrenceOverride(override);
    }

    @Transactional
    public void deleteRecurrenceEvent(Long accountId, Long recurrenceId) {
        recurrenceEventCommandService.lockRecurrenceEvent(accountId, recurrenceId);
        recurrenceShareCommandService.deleteAllForSourceRecurrence(recurrenceId);
        recurrenceEventCommandService.deleteRecurrenceOverridesByRecurrenceEventIds(List.of(recurrenceId));
        eventCommandService.deleteEventsByRecurrenceEventIds(List.of(recurrenceId));
        recurrenceEventCommandService.deleteRecurrenceEventsByIds(List.of(recurrenceId));
    }

    @Transactional
    public void deleteRecurrenceOccurrence(Long accountId, Long recurrenceId, Instant originStartAt) {
        RecurrenceEvent recurrenceEvent = recurrenceEventCommandService
                .lockRecurrenceEvent(accountId, recurrenceId);
        Optional<RecurrenceEventOverride> existingOverride =
                findOverrideOrRejectIneligible(recurrenceEvent, originStartAt);
        Instant deletedAt = Instant.now(clock);
        recurrenceEventCommandService.deleteRecurrenceOccurrence(
                recurrenceEvent,
                existingOverride,
                originStartAt,
                deletedAt
        );
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
        Optional<RecurrenceEventOverride> existingOverride =
                recurrenceEventQueryService.getOverrideIfExists(recurrenceEvent.getId(), originStartAt);
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
}
