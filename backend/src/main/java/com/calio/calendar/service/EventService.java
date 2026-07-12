package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CreateEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.controller.dto.UpdateImportantEventRequest;
import com.calio.calendar.controller.dto.UpdateEventRequest;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.AccountRepository;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.repository.RecurrenceEventRepository;
import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.Event;
import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.RecurrenceEventOverride;
import com.calio.calendar.repository.entity.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final RecurrenceEventRepository recurrenceEventRepository;
    private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;
    private final AccountRepository accountRepository;
    private final TagService tagService;

    public EventService(
            EventRepository eventRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository,
            AccountRepository accountRepository,
            TagService tagService
    ) {
        this.eventRepository = eventRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
        this.accountRepository = accountRepository;
        this.tagService = tagService;
    }

    @Transactional
    public EventResponse createEvent(Long accountId, CreateEventRequest request) {
        validateEventTimeRange(request.startAt(), request.endAt());
        Account account = accountRepository.getReferenceById(accountId);
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
        Event event = eventRepository.save(request.toEntity(tag, account));
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long accountId, Long eventId) {
        Event event = findEvent(accountId, eventId);
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse updateEvent(Long accountId, Long eventId, UpdateEventRequest request) {
        validateEventTimeRange(request.startAt(), request.endAt());
        Event event = findEvent(accountId, eventId);
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
        event.replace(request.title(), request.description(), request.startAt(), request.endAt(), tag);
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
        eventRepository.delete(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents(Long accountId, Instant from, Instant to) {
        validateListTimeRange(from, to);
        List<EventResponse> normalEvents = eventRepository.findNormalEvents(accountId, from, to)
                .stream()
                .map(EventResponse::from)
                .toList();
        List<EventResponse> recurrenceOccurrences = listRecurrenceOccurrences(accountId, from, to);

        List<EventResponse> responses = new ArrayList<>(normalEvents);
        responses.addAll(recurrenceOccurrences);
        responses.sort(Comparator.comparing(EventResponse::startAt));
        return responses;
    }

    private List<EventResponse> listRecurrenceOccurrences(Long accountId, Instant from, Instant to) {
        List<RecurrenceEvent> recurrenceEvents = recurrenceEventRepository.findEligibleRules(
                accountId,
                toUtcDate(from).minusDays(1),
                toUtcDate(to)
        );
        List<EventResponse> responses = new ArrayList<>();
        Set<OccurrenceKey> responseKeys = new HashSet<>();

        for (RecurrenceEvent recurrenceEvent : recurrenceEvents) {
            responses.addAll(baseOccurrenceResponses(recurrenceEvent, from, to, responseKeys));
        }

        recurrenceEventOverrideRepository.findModifiedOverlappingOverrides(accountId, from, to)
                .stream()
                .filter(override -> responseKeys.add(OccurrenceKey.from(override)))
                .map(override -> EventResponse.recurrenceOccurrence(
                        override.getRecurrenceEvent(),
                        override.getOriginStartAt(),
                        override.getOverrideStartAt(),
                        override.getOverrideEndAt()
                ))
                .forEach(responses::add);
        return responses.stream()
                .filter(response -> overlaps(response.startAt(), response.endAt(), from, to))
                .toList();
    }

    private List<EventResponse> baseOccurrenceResponses(
            RecurrenceEvent recurrenceEvent,
            Instant from,
            Instant to,
            Set<OccurrenceKey> responseKeys
    ) {
        List<Instant> originStartAts = originStartAts(recurrenceEvent);
        Set<Instant> overriddenOrigins = recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAtIn(recurrenceEvent.getId(), originStartAts)
                .stream()
                .map(RecurrenceEventOverride::getOriginStartAt)
                .collect(Collectors.toSet());

        return originStartAts.stream()
                .filter(originStartAt -> !overriddenOrigins.contains(originStartAt))
                .map(originStartAt -> EventResponse.recurrenceOccurrence(
                        recurrenceEvent,
                        originStartAt,
                        originStartAt,
                        toOccurrenceEndAt(recurrenceEvent, originStartAt)
                ))
                .filter(response -> overlaps(response.startAt(), response.endAt(), from, to))
                .filter(response -> responseKeys.add(OccurrenceKey.from(response)))
                .toList();
    }

    private List<Instant> originStartAts(RecurrenceEvent recurrenceEvent) {
        List<Instant> originStartAts = new ArrayList<>();
        int intervalIndex = 0;
        Instant originStartAt = occurrenceStartAt(recurrenceEvent, intervalIndex);
        Instant recurrenceEndAt = recurrenceEndAt(recurrenceEvent);

        while (originStartAt.isBefore(recurrenceEndAt)) {
            originStartAts.add(originStartAt);
            intervalIndex++;
            originStartAt = occurrenceStartAt(recurrenceEvent, intervalIndex);
        }

        return originStartAts;
    }

    private Instant occurrenceStartAt(RecurrenceEvent recurrenceEvent, int intervalIndex) {
        LocalDate startDate = recurrenceEvent.getRecurrenceStartDate();
        LocalDate occurrenceDate = switch (recurrenceEvent.getRecurrenceFrequency()) {
            case DAILY -> startDate.plusDays(intervalIndex);
            case WEEKLY -> startDate.plusWeeks(intervalIndex);
            case MONTHLY -> startDate.plusMonths(intervalIndex);
            case YEARLY -> startDate.plusYears(intervalIndex);
        };
        return toInstant(occurrenceDate, recurrenceEvent.getRecurrenceStartTime());
    }

    private Instant toOccurrenceEndAt(RecurrenceEvent recurrenceEvent, Instant originStartAt) {
        LocalDate occurrenceDate = originStartAt.atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = recurrenceEvent.getRecurrenceStartTime()
                .isBefore(recurrenceEvent.getRecurrenceEndTime())
                ? occurrenceDate
                : occurrenceDate.plusDays(1);
        return toInstant(endDate, recurrenceEvent.getRecurrenceEndTime());
    }

    private Instant recurrenceEndAt(RecurrenceEvent recurrenceEvent) {
        return toInstant(recurrenceEvent.getRecurrenceEndDate(), recurrenceEvent.getRecurrenceEndTime());
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).toInstant(ZoneOffset.UTC);
    }

    private LocalDate toUtcDate(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalDate();
    }

    private boolean overlaps(Instant startAt, Instant endAt, Instant from, Instant to) {
        return startAt.isBefore(to) && endAt.isAfter(from);
    }

    private Event findEvent(Long accountId, Long eventId) {
        return eventRepository.findByIdAndAccount_IdAndDeletedAtIsNull(eventId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.EVENT_NOT_FOUND));
    }

    private void validateEventTimeRange(Instant startAt, Instant endAt) {
        if (startAt.isBefore(endAt)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }

    private void validateListTimeRange(Instant from, Instant to) {
        if (!from.isAfter(to)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }

    private record OccurrenceKey(Long recurrenceId, Instant originStartAt) {

        private static OccurrenceKey from(EventResponse response) {
            return new OccurrenceKey(response.recurrenceId(), response.originStartAt());
        }

        private static OccurrenceKey from(RecurrenceEventOverride override) {
            return new OccurrenceKey(override.getRecurrenceId(), override.getOriginStartAt());
        }
    }
}
