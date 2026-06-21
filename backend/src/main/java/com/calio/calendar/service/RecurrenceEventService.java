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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        recurrenceEvent.replace(
                request.title().applyTo(recurrenceEvent.getRecurrenceTitle()),
                request.description().applyTo(recurrenceEvent.getRecurrenceDescription()),
                toUtcDate(request.startAt()),
                toUtcDate(request.endAt()),
                toUtcTime(request.startAt()),
                toUtcTime(request.endAt()),
                request.recurrenceFrequency()
        );
        rebuildOccurrenceEvents(recurrenceEvent);
        recurrenceEventRepository.flush();

        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    private RecurrenceEvent findRecurrenceEvent(Long recurrenceId) {
        return recurrenceEventRepository.findById(recurrenceId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }

    private List<Event> toOccurrenceEvents(RecurrenceEvent recurrenceEvent) {
        return occurrenceDates(recurrenceEvent)
                .stream()
                .map(occurrenceDate -> toOccurrenceEvent(recurrenceEvent, occurrenceDate))
                .toList();
    }

    private void rebuildOccurrenceEvents(RecurrenceEvent recurrenceEvent) {
        Map<LocalDate, Event> existingOccurrences = existingOccurrencesByDate(recurrenceEvent);
        List<Event> newOccurrences = new ArrayList<>();

        for (LocalDate occurrenceDate : occurrenceDates(recurrenceEvent)) {
            Event existingOccurrence = existingOccurrences.remove(occurrenceDate);
            if (existingOccurrence == null) {
                newOccurrences.add(toOccurrenceEvent(recurrenceEvent, occurrenceDate));
                continue;
            }

            existingOccurrence.replace(
                    recurrenceEvent.getRecurrenceTitle(),
                    recurrenceEvent.getRecurrenceDescription(),
                    toInstant(occurrenceDate, recurrenceEvent.getRecurrenceStartTime()),
                    toInstant(occurrenceDate, recurrenceEvent.getRecurrenceEndTime())
            );
        }

        eventRepository.deleteAll(existingOccurrences.values());
        eventRepository.saveAll(newOccurrences);
    }

    private Map<LocalDate, Event> existingOccurrencesByDate(RecurrenceEvent recurrenceEvent) {
        Map<LocalDate, Event> existingOccurrences = new HashMap<>();
        for (Event event : eventRepository.findByRecurrenceIdOrderByStartAtAsc(recurrenceEvent.getId())) {
            existingOccurrences.put(toUtcDate(event.getStartAt()), event);
        }

        return existingOccurrences;
    }

    private List<LocalDate> occurrenceDates(RecurrenceEvent recurrenceEvent) {
        List<LocalDate> occurrenceDates = new ArrayList<>();
        int intervalIndex = 0;
        LocalDate occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);

        while (!occurrenceDate.isAfter(recurrenceEvent.getRecurrenceEndDate())) {
            occurrenceDates.add(occurrenceDate);
            intervalIndex++;
            occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);
        }

        return occurrenceDates;
    }

    private Event toOccurrenceEvent(RecurrenceEvent recurrenceEvent, LocalDate occurrenceDate) {
        return new Event(
                recurrenceEvent.getRecurrenceTitle(),
                recurrenceEvent.getRecurrenceDescription(),
                toInstant(occurrenceDate, recurrenceEvent.getRecurrenceStartTime()),
                toInstant(occurrenceDate, recurrenceEvent.getRecurrenceEndTime()),
                recurrenceEvent.getId()
        );
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).toInstant(ZoneOffset.UTC);
    }

    private LocalDate toUtcDate(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalDate();
    }

    private LocalTime toUtcTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalTime();
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
