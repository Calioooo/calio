package com.calio.calendar.event.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.CreateEventRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.controller.dto.UpdateImportantEventRequest;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.sharing.event.service.PersonalEventGroupShareCommandService;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagQueryService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventService {

    private static final Duration MAX_EVENT_QUERY_RANGE = Duration.ofDays(366);

    private final EventQueryService eventQueryService;
    private final EventCommandService eventCommandService;
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService;
    private final AccountQueryService accountQueryService;
    private final TagQueryService tagQueryService;
    private final RecurrenceEventQueryService recurrenceEventQueryService;
    private final Rfc5545RecurrenceEngine recurrenceEngine;
    private final PersonalEventGroupShareCommandService eventShareCommandService;

    public EventService(
            EventQueryService eventQueryService,
            EventCommandService eventCommandService,
            GoogleCalendarEventMappingQueryService eventMappingQueryService,
            AccountQueryService accountQueryService,
            TagQueryService tagQueryService,
            RecurrenceEventQueryService recurrenceEventQueryService,
            Rfc5545RecurrenceEngine recurrenceEngine,
            PersonalEventGroupShareCommandService eventShareCommandService
    ) {
        this.eventQueryService = eventQueryService;
        this.eventCommandService = eventCommandService;
        this.eventMappingQueryService = eventMappingQueryService;
        this.accountQueryService = accountQueryService;
        this.tagQueryService = tagQueryService;
        this.recurrenceEventQueryService = recurrenceEventQueryService;
        this.recurrenceEngine = recurrenceEngine;
        this.eventShareCommandService = eventShareCommandService;
    }

    @Transactional
    public EventResponse createEvent(Long accountId, CreateEventRequest request) {
        CanonicalSchedule.event(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
        Account account = accountQueryService.getAccount(accountId);
        Tag tag = tagQueryService.getTagOrDefault(accountId, request.tagId());
        Event event = eventCommandService.createEvent(request.toEntity(tag, account));
        return EventResponse.from(event);
    }

    public EventResponse getEvent(Long accountId, Long eventId) {
        return EventResponse.from(eventQueryService.getEvent(accountId, eventId));
    }

    @Transactional
    public EventResponse updateEvent(Long accountId, Long eventId, UpdateEventRequest request) {
        Event event = eventCommandService.lockEvent(accountId, eventId);
        rejectExternalEventMutation(accountId, eventId);
        CanonicalSchedule schedule = CanonicalSchedule.event(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
        Tag tag = tagQueryService.getTagOrDefault(accountId, request.tagId());
        eventCommandService.updateEvent(event, request, schedule, tag);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse updateImportantEvent(Long accountId, Long eventId, UpdateImportantEventRequest request) {
        Event event = eventCommandService.lockEvent(accountId, eventId);
        rejectExternalEventMutation(accountId, eventId);
        eventCommandService.updateImportantEvent(event, request.importantEvent());
        return EventResponse.from(event);
    }

    @Transactional
    public void deleteEvent(Long accountId, Long eventId) {
        Event event = eventCommandService.lockEvent(accountId, eventId);
        rejectExternalEventMutation(accountId, eventId);
        eventShareCommandService.deleteAllForSourceEvent(eventId);
        eventCommandService.deleteEvent(event);
    }

    public List<EventResponse> listEvents(Long accountId, Instant from, Instant to) {
        validateListTimeRange(from, to);
        List<EventResponse> responses = eventQueryService.listEvents(accountId, from, to)
                .stream()
                .map(EventResponse::from)
                .collect(Collectors.toCollection(ArrayList::new));
        responses.addAll(listRecurrenceOccurrences(accountId, from, to));
        responses.sort(Comparator.comparing(EventResponse::startAt));
        return responses;
    }

    private List<EventResponse> listRecurrenceOccurrences(Long accountId, Instant from, Instant to) {
        List<EventResponse> responses = new ArrayList<>();
        Set<OccurrenceKey> responseKeys = new HashSet<>();
        List<RecurrenceEvent> recurrenceEvents =
                recurrenceEventQueryService.listExpansionCandidatesStartedBefore(accountId, to);
        for (RecurrenceEvent recurrenceEvent : recurrenceEvents) {
            addExpandedOccurrences(recurrenceEvent, from, to, responseKeys, responses);
        }
        addMovedInOverrides(accountId, from, to, responseKeys, responses);
        return responses;
    }

    private void addExpandedOccurrences(
            RecurrenceEvent recurrenceEvent,
            Instant from,
            Instant to,
            Set<OccurrenceKey> responseKeys,
            List<EventResponse> responses
    ) {
        List<RecurrenceOccurrence> occurrences = recurrenceEngine.expand(
                RecurrenceSchedule.from(recurrenceEvent),
                recurrenceEvent.getRecurrenceRules(),
                from,
                to
        );
        Map<Instant, RecurrenceEventOverride> overridesByOrigin = findOverridesByOrigin(recurrenceEvent, occurrences);
        for (RecurrenceOccurrence occurrence : occurrences) {
            OccurrenceKey key = new OccurrenceKey(recurrenceEvent.getId(), occurrence.originStartAt());
            RecurrenceEventOverride override = overridesByOrigin.get(occurrence.originStartAt());
            EventResponse response = finalOccurrenceResponse(recurrenceEvent, occurrence, override);
            if (response != null && overlaps(response, from, to) && responseKeys.add(key)) {
                responses.add(response);
            }
        }
    }

    private Map<Instant, RecurrenceEventOverride> findOverridesByOrigin(
            RecurrenceEvent recurrenceEvent,
            List<RecurrenceOccurrence> occurrences
    ) {
        List<Instant> originStartAts = occurrences.stream()
                .map(RecurrenceOccurrence::originStartAt)
                .toList();
        if (originStartAts.isEmpty()) {
            return Map.of();
        }
        return recurrenceEventQueryService
                .listOverrides(recurrenceEvent.getId(), originStartAts)
                .stream()
                .collect(Collectors.toMap(
                        RecurrenceEventOverride::getOriginStartAt,
                        Function.identity()
                ));
    }

    private EventResponse finalOccurrenceResponse(
            RecurrenceEvent recurrenceEvent,
            RecurrenceOccurrence occurrence,
            RecurrenceEventOverride override
    ) {
        if (override == null) {
            return EventResponse.recurrenceOccurrence(recurrenceEvent, occurrence);
        }
        return override.isDeleted() ? null : EventResponse.recurrenceOverride(override);
    }

    private void addMovedInOverrides(
            Long accountId,
            Instant from,
            Instant to,
            Set<OccurrenceKey> responseKeys,
            List<EventResponse> responses
    ) {
        recurrenceEventQueryService.listActiveOverlappingOverrides(accountId, from, to)
                .stream()
                .filter(override -> responseKeys.add(OccurrenceKey.from(override)))
                .map(EventResponse::recurrenceOverride)
                .forEach(responses::add);
    }

    private boolean overlaps(EventResponse response, Instant from, Instant to) {
        return response.startAt().isBefore(to) && response.endAt().isAfter(from);
    }

    private void validateListTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
        }
        if (Duration.between(from, to).compareTo(MAX_EVENT_QUERY_RANGE) > 0) {
            throw new CalioException(ErrorCode.EVENT_QUERY_RANGE_TOO_LARGE);
        }
    }

    private void rejectExternalEventMutation(Long accountId, Long eventId) {
        if (eventMappingQueryService.hasExternalEventMapping(eventId, accountId)) {
            throw new CalioException(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED);
        }
    }

    private record OccurrenceKey(Long recurrenceId, Instant originStartAt) {

        private static OccurrenceKey from(RecurrenceEventOverride override) {
            return new OccurrenceKey(override.getRecurrenceId(), override.getOriginStartAt());
        }
    }
}
