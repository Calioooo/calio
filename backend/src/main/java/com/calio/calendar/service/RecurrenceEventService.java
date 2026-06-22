package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.RecurrenceEventRepository;
import com.calio.calendar.repository.entity.Event;
import com.calio.calendar.repository.entity.RecurrenceEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecurrenceEventService {

    private final RecurrenceEventRepository recurrenceEventRepository;
    private final EventRepository eventRepository;

    public RecurrenceEventService(
            RecurrenceEventRepository recurrenceEventRepository,
            EventRepository eventRepository
    ) {
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public RecurrenceEventResponse createRecurrenceEvent(CreateRecurrenceEventRequest request) {
        validateRecurrenceDateRange(request.recurrenceStartDate(), request.recurrenceEndDate());
        validateRecurrenceTimeRange(request.recurrenceStartTime(), request.recurrenceEndTime());

        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.save(request.toEntity());
        eventRepository.saveAll(toOccurrenceEvents(recurrenceEvent));

        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    public RecurrenceEventResponse getRecurrenceEvent(Long recurrenceId) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEvent(recurrenceId);
        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    @Transactional
    public RecurrenceEventResponse updateRecurrenceEvent(Long recurrenceId, UpdateRecurrenceEventRequest request) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEvent(recurrenceId);
        validateProvidedTitle(request.title());

        Instant effectiveStartAt = request.startAt() == null
                ? toInstant(recurrenceEvent.getRecurrenceStartDate(), recurrenceEvent.getRecurrenceStartTime())
                : request.startAt();
        Instant effectiveEndAt = request.endAt() == null
                ? toInstant(recurrenceEvent.getRecurrenceEndDate(), recurrenceEvent.getRecurrenceEndTime())
                : request.endAt();
        validateRecurrenceUpdateTimeRange(effectiveStartAt, effectiveEndAt);

        recurrenceEvent.update(
                request.title() == null ? recurrenceEvent.getRecurrenceTitle() : request.title(),
                request.description() == null ? recurrenceEvent.getRecurrenceDescription() : request.description(),
                request.startAt() == null
                        ? recurrenceEvent.getRecurrenceStartDate()
                        : request.startAt().atOffset(ZoneOffset.UTC).toLocalDate(),
                request.endAt() == null
                        ? recurrenceEvent.getRecurrenceEndDate()
                        : request.endAt().atOffset(ZoneOffset.UTC).toLocalDate(),
                request.startAt() == null
                        ? recurrenceEvent.getRecurrenceStartTime()
                        : request.startAt().atOffset(ZoneOffset.UTC).toLocalTime(),
                request.endAt() == null
                        ? recurrenceEvent.getRecurrenceEndTime()
                        : request.endAt().atOffset(ZoneOffset.UTC).toLocalTime(),
                request.recurrenceFrequency() == null
                        ? recurrenceEvent.getRecurrenceFrequency()
                        : request.recurrenceFrequency()
        );
        recurrenceEventRepository.flush();
        rebuildOccurrenceEvents(recurrenceEvent);

        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    private RecurrenceEvent findRecurrenceEvent(Long recurrenceId) {
        return recurrenceEventRepository.findById(recurrenceId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }

    private void validateProvidedTitle(String title) {
        if (title == null || !title.isBlank()) {
            return;
        }

        throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }

    private void validateRecurrenceUpdateTimeRange(Instant startAt, Instant endAt) {
        if (startAt.isBefore(endAt)) {
            return;
        }

        throw new CalioException(ErrorCode.RECURRENCE_UPDATE_TIME_RANGE_INVALID);
    }

    private void rebuildOccurrenceEvents(RecurrenceEvent recurrenceEvent) {
        List<Event> staleEvents = new ArrayList<>(
                eventRepository.findByRecurrenceIdOrderByStartAtAsc(recurrenceEvent.getId())
        );
        List<Event> rebuiltEvents = new ArrayList<>();

        for (LocalDate occurrenceDate : occurrenceDates(recurrenceEvent)) {
            Event occurrence = findAndRemoveOccurrence(staleEvents, occurrenceDate)
                    .orElseGet(() -> toOccurrenceEvent(recurrenceEvent, occurrenceDate));
            occurrence.replace(
                    recurrenceEvent.getRecurrenceTitle(),
                    recurrenceEvent.getRecurrenceDescription(),
                    toInstant(occurrenceDate, recurrenceEvent.getRecurrenceStartTime()),
                    toOccurrenceEndInstant(recurrenceEvent, occurrenceDate)
            );
            rebuiltEvents.add(occurrence);
        }

        eventRepository.deleteAll(staleEvents);
        eventRepository.saveAll(rebuiltEvents);
    }

    private List<LocalDate> occurrenceDates(RecurrenceEvent recurrenceEvent) {
        List<LocalDate> occurrenceDates = new ArrayList<>();
        int intervalIndex = 0;
        LocalDate occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);

        while (isOccurrenceStartBeforeRuleEnd(recurrenceEvent, occurrenceDate)) {
            occurrenceDates.add(occurrenceDate);
            intervalIndex++;
            occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);
        }

        return occurrenceDates;
    }

    private Optional<Event> findAndRemoveOccurrence(List<Event> events, LocalDate occurrenceDate) {
        Iterator<Event> iterator = events.iterator();
        while (iterator.hasNext()) {
            Event event = iterator.next();
            if (occurrenceDateOf(event).equals(occurrenceDate)) {
                iterator.remove();
                return Optional.of(event);
            }
        }

        return Optional.empty();
    }

    private LocalDate occurrenceDateOf(Event event) {
        return event.getStartAt().atOffset(ZoneOffset.UTC).toLocalDate();
    }

    private List<Event> toOccurrenceEvents(RecurrenceEvent recurrenceEvent) {
        List<Event> occurrences = new ArrayList<>();
        int intervalIndex = 0;
        LocalDate occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);

        while (isOccurrenceStartBeforeRuleEnd(recurrenceEvent, occurrenceDate)) {
            occurrences.add(toOccurrenceEvent(recurrenceEvent, occurrenceDate));
            intervalIndex++;
            occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);
        }

        return occurrences;
    }

    private Event toOccurrenceEvent(RecurrenceEvent recurrenceEvent, LocalDate occurrenceDate) {
        return new Event(
                recurrenceEvent.getRecurrenceTitle(),
                recurrenceEvent.getRecurrenceDescription(),
                toInstant(occurrenceDate, recurrenceEvent.getRecurrenceStartTime()),
                toOccurrenceEndInstant(recurrenceEvent, occurrenceDate),
                recurrenceEvent.getId()
        );
    }

    private Instant toOccurrenceEndInstant(RecurrenceEvent recurrenceEvent, LocalDate occurrenceDate) {
        LocalDate endDate = occurrenceEndDate(recurrenceEvent, occurrenceDate);
        return toInstant(endDate, recurrenceEvent.getRecurrenceEndTime());
    }

    private LocalDate occurrenceEndDate(RecurrenceEvent recurrenceEvent, LocalDate occurrenceDate) {
        if (recurrenceEvent.getRecurrenceStartTime().isBefore(recurrenceEvent.getRecurrenceEndTime())) {
            return occurrenceDate;
        }

        return occurrenceDate.plusDays(1);
    }

    private boolean isOccurrenceStartBeforeRuleEnd(RecurrenceEvent recurrenceEvent, LocalDate occurrenceDate) {
        Instant occurrenceStartAt = toInstant(occurrenceDate, recurrenceEvent.getRecurrenceStartTime());
        Instant ruleEndAt = toInstant(recurrenceEvent.getRecurrenceEndDate(), recurrenceEvent.getRecurrenceEndTime());
        return occurrenceStartAt.isBefore(ruleEndAt);
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).toInstant(ZoneOffset.UTC);
    }

    private LocalDate occurrenceDate(RecurrenceEvent recurrenceEvent, int intervalIndex) {
        LocalDate startDate = recurrenceEvent.getRecurrenceStartDate();

        return switch (recurrenceEvent.getRecurrenceFrequency()) {
            case DAILY -> startDate.plusDays(intervalIndex);
            case WEEKLY -> startDate.plusWeeks(intervalIndex);
            case MONTHLY -> startDate.plusMonths(intervalIndex);
            case YEARLY -> startDate.plusYears(intervalIndex);
        };
    }

    private void validateRecurrenceDateRange(LocalDate recurrenceStartDate, LocalDate recurrenceEndDate) {
        if (!recurrenceStartDate.isAfter(recurrenceEndDate)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_RECURRENCE_DATE_RANGE);
    }

    private void validateRecurrenceTimeRange(LocalTime recurrenceStartTime, LocalTime recurrenceEndTime) {
        if (recurrenceStartTime.isBefore(recurrenceEndTime)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_RECURRENCE_TIME_RANGE);
    }
}
