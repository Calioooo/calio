package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.repository.RecurrenceEventRepository;
import com.calio.calendar.repository.entity.Event;
import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.RecurrenceEventOverride;
import com.calio.calendar.repository.entity.RecurrenceFrequency;
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
    private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    public RecurrenceEventService(
            RecurrenceEventRepository recurrenceEventRepository,
            EventRepository eventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository
    ) {
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.eventRepository = eventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
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
        request.validateWholeUpdateScope();
        RecurrenceEvent recurrenceEvent = findRecurrenceEvent(recurrenceId);
        String recurrenceTitle = resolveTitle(recurrenceEvent, request);
        String recurrenceDescription = resolveDescription(recurrenceEvent, request);
        LocalDate recurrenceStartDate = resolveStartDate(recurrenceEvent, request);
        LocalDate recurrenceEndDate = resolveEndDate(recurrenceEvent, request);
        LocalTime recurrenceStartTime = resolveStartTime(recurrenceEvent, request);
        LocalTime recurrenceEndTime = resolveEndTime(recurrenceEvent, request);
        RecurrenceFrequency recurrenceFrequency = resolveFrequency(recurrenceEvent, request);

        validateRecurrenceDateRange(recurrenceStartDate, recurrenceEndDate);
        validateRecurrenceTimeRange(recurrenceStartTime, recurrenceEndTime);
        recurrenceEvent.replace(
                recurrenceTitle,
                recurrenceDescription,
                recurrenceStartDate,
                recurrenceEndDate,
                recurrenceStartTime,
                recurrenceEndTime,
                recurrenceFrequency
        );
        reconstructOccurrenceEvents(recurrenceEvent);
        recurrenceEventRepository.flush();
        eventRepository.flush();

        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    @Transactional
    public EventResponse updateSingleOccurrence(Long recurrenceId, UpdateRecurrenceEventRequest request) {
        request.validateSingleOccurrenceScope();
        findRecurrenceEvent(recurrenceId);
        Event occurrence = findOccurrenceEvent(recurrenceId, request.targetOccurrenceStartAt());
        Instant originStartAt = occurrence.getOriginStartAt();
        Instant originEndAt = occurrence.getOriginEndAt();

        recurrenceEventOverrideRepository.findByRecurrenceIdAndOriginStartAt(recurrenceId, originStartAt)
                .ifPresentOrElse(
                        override -> updateExistingOverride(override, request),
                        () -> createModificationOverride(recurrenceId, originStartAt, originEndAt, request)
                );
        occurrence.rescheduleRecurrenceOccurrence(request.modifiedStartAt(), request.modifiedEndAt());
        recurrenceEventOverrideRepository.flush();
        eventRepository.flush();

        return EventResponse.from(occurrence);
    }

    private RecurrenceEvent findRecurrenceEvent(Long recurrenceId) {
        return recurrenceEventRepository.findById(recurrenceId)
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_EVENT_NOT_FOUND));
    }

    private Event findOccurrenceEvent(Long recurrenceId, Instant targetOccurrenceStartAt) {
        return eventRepository.findFirstByRecurrenceIdAndOriginStartAt(recurrenceId, targetOccurrenceStartAt)
                .or(() -> eventRepository.findFirstByRecurrenceIdAndStartAt(recurrenceId, targetOccurrenceStartAt))
                .orElseThrow(() -> new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND));
    }

    private void updateExistingOverride(RecurrenceEventOverride override, UpdateRecurrenceEventRequest request) {
        if (!override.isDeleted()) {
            override.replaceModifiedTime(request.modifiedStartAt(), request.modifiedEndAt());
            return;
        }

        throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_DELETED);
    }

    private void createModificationOverride(
            Long recurrenceId,
            Instant originStartAt,
            Instant originEndAt,
            UpdateRecurrenceEventRequest request
    ) {
        recurrenceEventOverrideRepository.save(new RecurrenceEventOverride(
                recurrenceId,
                false,
                originStartAt,
                originEndAt,
                request.modifiedStartAt(),
                request.modifiedEndAt()
        ));
    }

    private List<Event> toOccurrenceEvents(RecurrenceEvent recurrenceEvent) {
        List<Event> occurrences = new ArrayList<>();
        int intervalIndex = 0;
        LocalDate occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);

        while (!occurrenceDate.isAfter(recurrenceEvent.getRecurrenceEndDate())) {
            occurrences.add(toOccurrenceEvent(recurrenceEvent, occurrenceDate));
            intervalIndex++;
            occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);
        }

        return occurrences;
    }

    private void reconstructOccurrenceEvents(RecurrenceEvent recurrenceEvent) {
        Map<LocalDate, Event> existingOccurrences = existingOccurrencesByOriginDate(recurrenceEvent.getId());
        List<Event> newlyIncludedOccurrences = new ArrayList<>();
        int intervalIndex = 0;
        LocalDate occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);

        while (!occurrenceDate.isAfter(recurrenceEvent.getRecurrenceEndDate())) {
            Event existingOccurrence = existingOccurrences.remove(occurrenceDate);
            if (existingOccurrence == null) {
                newlyIncludedOccurrences.add(toOccurrenceEvent(recurrenceEvent, occurrenceDate));
            } else {
                replaceOccurrenceEvent(existingOccurrence, recurrenceEvent, occurrenceDate);
            }
            intervalIndex++;
            occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);
        }

        eventRepository.deleteAll(existingOccurrences.values());
        eventRepository.saveAll(newlyIncludedOccurrences);
    }

    private Map<LocalDate, Event> existingOccurrencesByOriginDate(Long recurrenceId) {
        Map<LocalDate, Event> occurrencesByOriginDate = new HashMap<>();
        for (Event occurrence : eventRepository.findByRecurrenceIdOrderByStartAtAsc(recurrenceId)) {
            occurrencesByOriginDate.putIfAbsent(toUtcDate(occurrence.getOriginStartAt()), occurrence);
        }

        return occurrencesByOriginDate;
    }

    private void replaceOccurrenceEvent(
            Event occurrence,
            RecurrenceEvent recurrenceEvent,
            LocalDate occurrenceDate
    ) {
        Instant originStartAt = toInstant(occurrenceDate, recurrenceEvent.getRecurrenceStartTime());
        Instant originEndAt = toInstant(occurrenceDate, recurrenceEvent.getRecurrenceEndTime());
        occurrence.replaceRecurrenceOccurrence(
                recurrenceEvent.getRecurrenceTitle(),
                recurrenceEvent.getRecurrenceDescription(),
                originStartAt,
                originEndAt,
                originStartAt,
                originEndAt
        );
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
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
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

    private String resolveTitle(RecurrenceEvent recurrenceEvent, UpdateRecurrenceEventRequest request) {
        if (request.hasRecurrenceTitle()) {
            return request.recurrenceTitle();
        }

        return recurrenceEvent.getRecurrenceTitle();
    }

    private String resolveDescription(RecurrenceEvent recurrenceEvent, UpdateRecurrenceEventRequest request) {
        if (request.hasRecurrenceDescription()) {
            return request.recurrenceDescription();
        }

        return recurrenceEvent.getRecurrenceDescription();
    }

    private LocalDate resolveStartDate(RecurrenceEvent recurrenceEvent, UpdateRecurrenceEventRequest request) {
        if (request.hasRecurrenceStartDate()) {
            return request.recurrenceStartDate();
        }

        return recurrenceEvent.getRecurrenceStartDate();
    }

    private LocalDate resolveEndDate(RecurrenceEvent recurrenceEvent, UpdateRecurrenceEventRequest request) {
        if (request.hasRecurrenceEndDate()) {
            return request.recurrenceEndDate();
        }

        return recurrenceEvent.getRecurrenceEndDate();
    }

    private LocalTime resolveStartTime(RecurrenceEvent recurrenceEvent, UpdateRecurrenceEventRequest request) {
        if (request.hasRecurrenceStartTime()) {
            return request.recurrenceStartTime();
        }

        return recurrenceEvent.getRecurrenceStartTime();
    }

    private LocalTime resolveEndTime(RecurrenceEvent recurrenceEvent, UpdateRecurrenceEventRequest request) {
        if (request.hasRecurrenceEndTime()) {
            return request.recurrenceEndTime();
        }

        return recurrenceEvent.getRecurrenceEndTime();
    }

    private RecurrenceFrequency resolveFrequency(
            RecurrenceEvent recurrenceEvent,
            UpdateRecurrenceEventRequest request
    ) {
        if (!request.hasRecurrenceFrequency()) {
            return recurrenceEvent.getRecurrenceFrequency();
        }

        try {
            return RecurrenceFrequency.valueOf(request.recurrenceFrequency());
        } catch (IllegalArgumentException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_FREQUENCY);
        }
    }
}
