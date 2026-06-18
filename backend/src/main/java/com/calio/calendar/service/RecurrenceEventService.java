package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventCommand;
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
import java.util.Optional;
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
    public RecurrenceEventResponse updateWholeRecurrenceEvent(
            Long recurrenceId,
            UpdateRecurrenceEventCommand command
    ) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEvent(recurrenceId);
        UpdateRecurrenceEventRequest request = command.request();

        String recurrenceTitle = valueOrExisting(command, "recurrenceTitle",
                request.recurrenceTitle(), recurrenceEvent.getRecurrenceTitle());
        String recurrenceDescription = valueOrExisting(command, "recurrenceDescription",
                request.recurrenceDescription(), recurrenceEvent.getRecurrenceDescription());
        LocalDate recurrenceStartDate = valueOrExisting(command, "recurrenceStartDate",
                request.recurrenceStartDate(), recurrenceEvent.getRecurrenceStartDate());
        LocalDate recurrenceEndDate = valueOrExisting(command, "recurrenceEndDate",
                request.recurrenceEndDate(), recurrenceEvent.getRecurrenceEndDate());
        LocalTime recurrenceStartTime = valueOrExisting(command, "recurrenceStartTime",
                request.recurrenceStartTime(), recurrenceEvent.getRecurrenceStartTime());
        LocalTime recurrenceEndTime = valueOrExisting(command, "recurrenceEndTime",
                request.recurrenceEndTime(), recurrenceEvent.getRecurrenceEndTime());
        RecurrenceFrequency recurrenceFrequency = valueOrExisting(command, "recurrenceFrequency",
                request.recurrenceFrequency(), recurrenceEvent.getRecurrenceFrequency());

        validateRecurrenceDateRange(recurrenceStartDate, recurrenceEndDate);
        validateRecurrenceTimeRange(recurrenceStartTime, recurrenceEndTime);
        validateRecurrenceFrequency(recurrenceFrequency);

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
    public EventResponse updateSingleOccurrence(
            Long recurrenceId,
            UpdateRecurrenceEventRequest request
    ) {
        findRecurrenceEvent(recurrenceId);
        validateModifiedOccurrenceTimeRange(request.modifiedStartAt(), request.modifiedEndAt());

        Event occurrence = findOccurrenceEvent(recurrenceId, request.targetOccurrenceStartAt());
        Instant originStartAt = occurrence.getOriginStartAt().orElse(occurrence.getStartAt());
        Instant originEndAt = occurrence.getOriginEndAt().orElse(occurrence.getEndAt());
        Optional<RecurrenceEventOverride> override = recurrenceEventOverrideRepository
                .findByRecurrenceIdAndOriginStartAt(recurrenceId, originStartAt);

        if (override.map(RecurrenceEventOverride::isDeleted).orElse(false)) {
            throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_DELETED);
        }

        occurrence.moveRecurrenceOccurrence(request.modifiedStartAt(), request.modifiedEndAt());
        saveModificationOverride(recurrenceId, request, originStartAt, originEndAt, override);
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

    private void saveModificationOverride(
            Long recurrenceId,
            UpdateRecurrenceEventRequest request,
            Instant originStartAt,
            Instant originEndAt,
            Optional<RecurrenceEventOverride> override
    ) {
        if (override.isPresent()) {
            override.get().replaceModification(originEndAt, request.modifiedStartAt(), request.modifiedEndAt());
            return;
        }

        recurrenceEventOverrideRepository.save(new RecurrenceEventOverride(
                recurrenceId,
                originStartAt,
                originEndAt,
                request.modifiedStartAt(),
                request.modifiedEndAt()
        ));
    }

    private <T> T valueOrExisting(UpdateRecurrenceEventCommand command, String fieldName, T value, T existing) {
        if (command.hasField(fieldName)) {
            return value;
        }

        return existing;
    }

    private void reconstructOccurrenceEvents(RecurrenceEvent recurrenceEvent) {
        Map<Instant, Event> existingOccurrences = existingOccurrencesByOriginStartAt(recurrenceEvent.getId());

        for (Event nextOccurrence : toOccurrenceEvents(recurrenceEvent)) {
            Instant originStartAt = nextOccurrence.getOriginStartAt().orElse(nextOccurrence.getStartAt());
            Event existingOccurrence = existingOccurrences.remove(originStartAt);
            if (existingOccurrence == null) {
                eventRepository.save(nextOccurrence);
                continue;
            }

            existingOccurrence.replaceRecurrenceOccurrence(
                    nextOccurrence.getTitle(),
                    nextOccurrence.getDescription(),
                    nextOccurrence.getStartAt(),
                    nextOccurrence.getEndAt()
            );
        }

        eventRepository.deleteAll(existingOccurrences.values());
    }

    private Map<Instant, Event> existingOccurrencesByOriginStartAt(Long recurrenceId) {
        Map<Instant, Event> occurrencesByOriginStartAt = new HashMap<>();
        for (Event occurrence : eventRepository.findByRecurrenceIdOrderByStartAtAsc(recurrenceId)) {
            Instant originStartAt = occurrence.getOriginStartAt().orElse(occurrence.getStartAt());
            occurrencesByOriginStartAt.putIfAbsent(originStartAt, occurrence);
        }

        return occurrencesByOriginStartAt;
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

    private void validateModifiedOccurrenceTimeRange(Instant modifiedStartAt, Instant modifiedEndAt) {
        if (modifiedStartAt.isBefore(modifiedEndAt)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_RECURRENCE_TIME_RANGE);
    }

    private void validateRecurrenceFrequency(RecurrenceFrequency recurrenceFrequency) {
        if (recurrenceFrequency != null) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_RECURRENCE_FREQUENCY);
    }
}
