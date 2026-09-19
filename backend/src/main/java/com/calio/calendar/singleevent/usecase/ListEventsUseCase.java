package com.calio.calendar.singleevent.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.RecurrenceEventQueryService;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.singleevent.controller.dto.EventResponse;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
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
public class ListEventsUseCase {

    private static final Duration MAX_EVENT_QUERY_RANGE = Duration.ofDays(366);

    private final SingleEventRepository eventRepository;
    private final TagRepository tagRepository;
    private final RecurrenceEventQueryService recurrenceEventQueryService;
    private final Rfc5545RecurrenceEngine recurrenceEngine;

    public ListEventsUseCase(
            SingleEventRepository eventRepository,
            TagRepository tagRepository,
            RecurrenceEventQueryService recurrenceEventQueryService,
            Rfc5545RecurrenceEngine recurrenceEngine
    ) {
        this.eventRepository = eventRepository;
        this.tagRepository = tagRepository;
        this.recurrenceEventQueryService = recurrenceEventQueryService;
        this.recurrenceEngine = recurrenceEngine;
    }

    @Transactional(readOnly = true)
    public List<EventResponse> list(Long accountId, Instant from, Instant to) {
        validateTimeRange(from, to);
        List<SingleEvent> events = eventRepository.findSingleEvents(accountId, from, to);
        Map<Long, Tag> tagsById = tagRepository.findAllById(events.stream()
                        .map(SingleEvent::getTagId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
        List<EventResponse> responses = events.stream()
                .map(event -> EventResponse.from(event, requiredTag(tagsById, event.getTagId())))
                .collect(Collectors.toCollection(ArrayList::new));
        responses.addAll(listRecurrenceOccurrences(accountId, from, to));
        responses.sort(Comparator.comparing(EventResponse::startAt));
        return responses;
    }

    private Tag requiredTag(Map<Long, Tag> tagsById, Long tagId) {
        Tag tag = tagsById.get(tagId);
        if (tag == null) {
            throw new CalioException(ErrorCode.TAG_NOT_FOUND);
        }
        return tag;
    }

    private List<EventResponse> listRecurrenceOccurrences(Long accountId, Instant from, Instant to) {
        List<EventResponse> responses = new ArrayList<>();
        Set<OccurrenceKey> responseKeys = new HashSet<>();
        for (RecurrenceEvent recurrenceEvent : recurrenceEventQueryService
                .listExpansionCandidatesStartedBefore(accountId, to)) {
            addExpandedOccurrences(recurrenceEvent, from, to, responseKeys, responses);
        }
        recurrenceEventQueryService.listActiveOverlappingOverrides(accountId, from, to)
                .stream()
                .filter(override -> responseKeys.add(OccurrenceKey.from(override)))
                .map(EventResponse::recurrenceOverride)
                .forEach(responses::add);
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
                RecurrenceSchedule.from(recurrenceEvent), recurrenceEvent.getRecurrenceRules(), from, to);
        Map<Instant, RecurrenceEventOverride> overridesByOrigin = findOverridesByOrigin(recurrenceEvent, occurrences);
        for (RecurrenceOccurrence occurrence : occurrences) {
            OccurrenceKey key = new OccurrenceKey(recurrenceEvent.getId(), occurrence.originStartAt());
            RecurrenceEventOverride override = overridesByOrigin.get(occurrence.originStartAt());
            EventResponse response = override == null
                    ? EventResponse.recurrenceOccurrence(recurrenceEvent, occurrence)
                    : override.isDeleted() ? null : EventResponse.recurrenceOverride(override);
            if (response != null && overlaps(response, from, to) && responseKeys.add(key)) {
                responses.add(response);
            }
        }
    }

    private Map<Instant, RecurrenceEventOverride> findOverridesByOrigin(
            RecurrenceEvent recurrenceEvent,
            List<RecurrenceOccurrence> occurrences
    ) {
        List<Instant> originStartAts = occurrences.stream().map(RecurrenceOccurrence::originStartAt).toList();
        if (originStartAts.isEmpty()) {
            return Map.of();
        }
        return recurrenceEventQueryService.listOverrides(recurrenceEvent.getId(), originStartAts).stream()
                .collect(Collectors.toMap(RecurrenceEventOverride::getOriginStartAt, Function.identity()));
    }

    private boolean overlaps(EventResponse response, Instant from, Instant to) {
        return response.startAt().isBefore(to) && response.endAt().isAfter(from);
    }

    private void validateTimeRange(Instant from, Instant to) {
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
