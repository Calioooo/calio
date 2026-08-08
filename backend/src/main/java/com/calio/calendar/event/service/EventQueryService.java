package com.calio.calendar.event.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
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

@Service
public class EventQueryService {

    private static final Duration MAX_EVENT_QUERY_RANGE = Duration.ofDays(366);

    private final EventRepository eventRepository;
    private final RecurrenceEventRepository recurrenceEventRepository;
    private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;
    private final Rfc5545RecurrenceEngine recurrenceEngine;
    private final GoogleCalendarEventMappingRepository googleCalendarEventMappingRepository;

    public EventQueryService(
            EventRepository eventRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository,
            Rfc5545RecurrenceEngine recurrenceEngine,
            GoogleCalendarEventMappingRepository googleCalendarEventMappingRepository
    ) {
        this.eventRepository = eventRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
        this.recurrenceEngine = recurrenceEngine;
        this.googleCalendarEventMappingRepository = googleCalendarEventMappingRepository;
    }

    public Event findEvent(Long accountId, Long eventId) {
        return eventRepository.findByIdAndAccount_Id(eventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    }

    public boolean hasExternalEventMapping(Long accountId, Long eventId) {
        return googleCalendarEventMappingRepository
                .existsByEvent_IdAndIntegration_AccountId(eventId, accountId);
    }

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
                recurrenceEventRepository.findExpansionCandidatesStartedBefore(accountId, to);
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
        recurrenceEventOverrideRepository.findActiveOverlappingOverrides(accountId, from, to)
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

    private record OccurrenceKey(Long recurrenceId, Instant originStartAt) {

        private static OccurrenceKey from(RecurrenceEventOverride override) {
            return new OccurrenceKey(override.getRecurrenceId(), override.getOriginStartAt());
        }
    }
}
