package com.calio.calendar.event.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.CreateEventRequest;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.controller.dto.UpdateEventRequest;
import com.calio.calendar.event.controller.dto.UpdateImportantEventRequest;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.event.service.dto.CalendarFreeTime;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.service.TagService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
    private final AccountRepository accountRepository;
    private final TagService tagService;
    private final Rfc5545RecurrenceEngine recurrenceEngine;
    private final GoogleCalendarEventMappingRepository googleCalendarEventMappingRepository;

    public EventService(
            EventRepository eventRepository,
            RecurrenceEventRepository recurrenceEventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository,
            AccountRepository accountRepository,
            TagService tagService,
            Rfc5545RecurrenceEngine recurrenceEngine,
            GoogleCalendarEventMappingRepository googleCalendarEventMappingRepository
    ) {
        this.eventRepository = eventRepository;
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
        this.accountRepository = accountRepository;
        this.tagService = tagService;
        this.recurrenceEngine = recurrenceEngine;
        this.googleCalendarEventMappingRepository = googleCalendarEventMappingRepository;
    }

    @Transactional
    public EventResponse createEvent(Long accountId, CreateEventRequest request) {
        CanonicalSchedule.event(
                request.startAt(),
                request.endAt(),
                request.allDay(),
                request.timeZone()
        );
        Account account = accountRepository.getReferenceById(accountId);
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
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
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
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

    @Transactional(readOnly = true)
    public List<CalendarFreeTime> findAvailableTimes(
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            ZoneId timeZone,
            LocalTime availableFrom,
            LocalTime availableUntil,
            Duration minimumDuration
    ) {
        validateAvailabilityCriteria(availableFrom, availableUntil, minimumDuration);
        List<EventResponse> events = listEventsForDateRange(accountId, startDate, endDate, timeZone);
        List<CalendarFreeTime> availableTimes = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            addAvailableTimesForDate(
                    events,
                    date,
                    timeZone,
                    availableFrom,
                    availableUntil,
                    minimumDuration,
                    availableTimes
            );
        }
        return availableTimes;
    }

    private void validateAvailabilityCriteria(
            LocalTime availableFrom,
            LocalTime availableUntil,
            Duration minimumDuration
    ) {
        if (!availableFrom.isBefore(availableUntil)
                || minimumDuration.isZero()
                || minimumDuration.isNegative()) {
            throw new IllegalArgumentException("Availability window and duration must be positive.");
        }
    }

    private List<EventResponse> listEventsForDateRange(
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            ZoneId timeZone
    ) {
        Instant rangeStart = startDate.atStartOfDay(timeZone).toInstant();
        Instant rangeEnd = endDate.plusDays(1).atStartOfDay(timeZone).toInstant();
        return listEvents(accountId, rangeStart, rangeEnd);
    }

    private void addAvailableTimesForDate(
            List<EventResponse> events,
            LocalDate date,
            ZoneId timeZone,
            LocalTime availableFrom,
            LocalTime availableUntil,
            Duration minimumDuration,
            List<CalendarFreeTime> availableTimes
    ) {
        Instant windowStart = LocalDateTime.of(date, availableFrom).atZone(timeZone).toInstant();
        Instant windowEnd = LocalDateTime.of(date, availableUntil).atZone(timeZone).toInstant();
        List<TimeRange> occupiedRanges = findOccupiedRanges(events, windowStart, windowEnd);
        List<String> allDayEventTitles = findAllDayEventTitles(events, date);
        addAvailableGaps(
                windowStart,
                windowEnd,
                occupiedRanges,
                minimumDuration,
                allDayEventTitles,
                timeZone,
                availableTimes
        );
    }

    private List<TimeRange> findOccupiedRanges(
            List<EventResponse> events,
            Instant windowStart,
            Instant windowEnd
    ) {
        return events.stream()
                .filter(event -> !event.allDay())
                .map(event -> TimeRange.overlapping(event.startAt(), event.endAt(), windowStart, windowEnd))
                .filter(TimeRange::hasDuration)
                .sorted(Comparator.comparing(TimeRange::start))
                .toList();
    }

    private List<String> findAllDayEventTitles(List<EventResponse> events, LocalDate date) {
        return events.stream()
                .filter(EventResponse::allDay)
                .filter(event -> occursOnDate(event, date))
                .map(EventResponse::title)
                .toList();
    }

    private void addAvailableGaps(
            Instant windowStart,
            Instant windowEnd,
            List<TimeRange> occupiedRanges,
            Duration minimumDuration,
            List<String> allDayEventTitles,
            ZoneId timeZone,
            List<CalendarFreeTime> availableTimes
    ) {
        Instant availableStart = windowStart;
        for (TimeRange occupiedRange : occupiedRanges) {
            addAvailableTimeWhenLongEnough(
                    availableStart,
                    occupiedRange.start(),
                    minimumDuration,
                    allDayEventTitles,
                    timeZone,
                    availableTimes
            );
            if (occupiedRange.end().isAfter(availableStart)) {
                availableStart = occupiedRange.end();
            }
        }
        addAvailableTimeWhenLongEnough(
                availableStart,
                windowEnd,
                minimumDuration,
                allDayEventTitles,
                timeZone,
                availableTimes
        );
    }

    private void addAvailableTimeWhenLongEnough(
            Instant start,
            Instant end,
            Duration minimumDuration,
            List<String> allDayEventTitles,
            ZoneId timeZone,
            List<CalendarFreeTime> availableTimes
    ) {
        if (Duration.between(start, end).compareTo(minimumDuration) >= 0) {
            availableTimes.add(new CalendarFreeTime(
                    formatTime(start, timeZone),
                    formatTime(end, timeZone),
                    allDayEventTitles
            ));
        }
    }

    private boolean occursOnDate(EventResponse event, LocalDate date) {
        LocalDate startDate = event.startAt().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endDate = event.endAt().atZone(ZoneOffset.UTC).toLocalDate();
        return !date.isBefore(startDate) && date.isBefore(endDate);
    }

    private String formatTime(Instant instant, ZoneId timeZone) {
        return instant.atZone(timeZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private record TimeRange(Instant start, Instant end) {

        private static TimeRange overlapping(
                Instant start,
                Instant end,
                Instant rangeStart,
                Instant rangeEnd
        ) {
            Instant overlapStart = start.isAfter(rangeStart) ? start : rangeStart;
            Instant overlapEnd = end.isBefore(rangeEnd) ? end : rangeEnd;
            return new TimeRange(overlapStart, overlapEnd);
        }

        private boolean hasDuration() {
            return start.isBefore(end);
        }
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
        if (googleCalendarEventMappingRepository
                .existsByEvent_IdAndIntegration_AccountId(eventId, accountId)) {
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
