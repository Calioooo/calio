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
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
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
public class EventService {

    private static final Duration MAX_EVENT_QUERY_RANGE = Duration.ofDays(366);

    private final EventRepository eventRepository;
    private final RecurrenceEventRepository recurrenceEventRepository;
    private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;
    private final AccountQueryService accountQueryService;
    private final TagQueryService tagQueryService;
    private final Rfc5545RecurrenceEngine recurrenceEngine;
    private final GoogleCalendarEventMappingQueryService googleCalendarEventMappingQueryService;

    public EventService(
            EventRepository eventRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository,
            AccountQueryService accountQueryService,
            TagQueryService tagQueryService,
            Rfc5545RecurrenceEngine recurrenceEngine,
            GoogleCalendarEventMappingQueryService googleCalendarEventMappingQueryService
    ) {
        this.eventRepository = eventRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
        this.accountQueryService = accountQueryService;
        this.tagQueryService = tagQueryService;
        this.recurrenceEngine = recurrenceEngine;
        this.googleCalendarEventMappingQueryService = googleCalendarEventMappingQueryService;
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
        Event event = eventRepository.save(request.toEntity(tag, account));
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long accountId, Long eventId) {
        return EventResponse.from(findEvent(accountId, eventId));
    }

    @Transactional
    public EventResponse updateEvent(Long accountId, Long eventId, UpdateEventRequest request) {
        Event event = findEvent(accountId, eventId);
        rejectExternalEventMutation(accountId, eventId);
        CanonicalSchedule schedule = CanonicalSchedule.event(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
        Tag tag = tagQueryService.getTagOrDefault(accountId, request.tagId());
        event.replace(
                request.title(),
                request.description(),
                schedule.startAt(),
                schedule.endAt(),
                schedule.allDay(),
                schedule.timeZone(),
                tag
        );
        eventRepository.flush();
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse updateImportantEvent(Long accountId, Long eventId, UpdateImportantEventRequest request) {
        Event event = findEvent(accountId, eventId);
        event.changeImportantEvent(request.importantEvent());
        eventRepository.flush();
        return EventResponse.from(event);
    }

    @Transactional
    public void deleteEvent(Long accountId, Long eventId) {
        Event event = findEvent(accountId, eventId);
        rejectExternalEventMutation(accountId, eventId);
        eventRepository.delete(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(Long accountId, Instant from, Instant to) {
        validateListTimeRange(from, to);
        List<EventResponse> responses = eventRepository.findNormalEvents(accountId, from, to)
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
                recurrenceEventRepository.findCandidatesStartedBefore(accountId, to);
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
        return recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAtIn(recurrenceEvent.getId(), originStartAts)
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
        recurrenceEventOverrideRepository.findModifiedOverlappingOverrides(accountId, from, to)
                .stream()
                .filter(override -> responseKeys.add(OccurrenceKey.from(override)))
                .map(EventResponse::recurrenceOverride)
                .forEach(responses::add);
    }

    private boolean overlaps(EventResponse response, Instant from, Instant to) {
        return response.startAt().isBefore(to) && response.endAt().isAfter(from);
    }

    private Event findEvent(Long accountId, Long eventId) {
        return eventRepository.findByIdAndAccount_Id(eventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    }

    private void rejectExternalEventMutation(Long accountId, Long eventId) {
        if (googleCalendarEventMappingQueryService.hasExternalEventMapping(eventId, accountId)) {
            throw new CalioException(ErrorCode.EXTERNAL_EVENT_MUTATION_NOT_SUPPORTED);
        }
    }

    private void validateListTimeRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
        }
        if (Duration.between(from, to).compareTo(MAX_EVENT_QUERY_RANGE) > 0) {
            throw new CalioException(ErrorCode.EVENT_QUERY_RANGE_TOO_LARGE);
        }
    }

    private record OccurrenceKey(Long recurrenceId, Instant originStartAt) {

        private static OccurrenceKey from(RecurrenceEventOverride override) {
            return new OccurrenceKey(override.getRecurrenceId(), override.getOriginStartAt());
        }
    }
}
