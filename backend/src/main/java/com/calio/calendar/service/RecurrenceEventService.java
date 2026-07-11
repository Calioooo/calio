package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.AccountRepository;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.repository.RecurrenceEventRepository;
import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.RecurrenceEventOverride;
import com.calio.calendar.repository.entity.Tag;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecurrenceEventService {

    private final RecurrenceEventRepository recurrenceEventRepository;
    private final EventRepository eventRepository;
    private final RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;
    private final AccountRepository accountRepository;
    private final TagService tagService;

    public RecurrenceEventService(
            RecurrenceEventRepository recurrenceEventRepository,
            EventRepository eventRepository,
            RecurrenceEventOverrideRepository recurrenceEventOverrideRepository,
            AccountRepository accountRepository,
            TagService tagService
    ) {
        this.recurrenceEventRepository = recurrenceEventRepository;
        this.eventRepository = eventRepository;
        this.recurrenceEventOverrideRepository = recurrenceEventOverrideRepository;
        this.accountRepository = accountRepository;
        this.tagService = tagService;
    }

    @Transactional
    public RecurrenceEventResponse createRecurrenceEvent(Long accountId, CreateRecurrenceEventRequest request) {
        validateRecurrenceDateRange(request.recurrenceStartDate(), request.recurrenceEndDate());
        validateRecurrenceTimeRange(request.recurrenceStartTime(), request.recurrenceEndTime());

        Account account = accountRepository.getReferenceById(accountId);
        Tag tag = tagService.getTagOrDefault(accountId, request.tagId());
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.save(request.toEntity(tag, account));

        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    public RecurrenceEventResponse getRecurrenceEvent(Long accountId, Long recurrenceId) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEvent(accountId, recurrenceId);
        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    @Transactional
    public RecurrenceEventResponse updateRecurrenceEvent(
            Long accountId,
            Long recurrenceId,
            UpdateRecurrenceEventRequest request
    ) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEvent(accountId, recurrenceId);
        validateProvidedTitle(request.title());

        Instant effectiveStartAt = request.startAt() == null
                ? toInstant(recurrenceEvent.getRecurrenceStartDate(), recurrenceEvent.getRecurrenceStartTime())
                : request.startAt();
        Instant effectiveEndAt = request.endAt() == null
                ? toInstant(recurrenceEvent.getRecurrenceEndDate(), recurrenceEvent.getRecurrenceEndTime())
                : request.endAt();
        validateRecurrenceUpdateTimeRange(effectiveStartAt, effectiveEndAt);

        Tag tag = request.tagId() == null
                ? recurrenceEvent.getTag()
                : tagService.getTag(accountId, request.tagId());
        recurrenceEvent.changeTag(tag);
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
        recurrenceEventOverrideRepository.deleteByRecurrenceEvent_Id(recurrenceId);

        return RecurrenceEventResponse.from(recurrenceEvent);
    }

    @Transactional
    public EventResponse updateRecurrenceOccurrence(
            Long accountId,
            Long recurrenceId,
            UpdateRecurrenceOccurrenceRequest request
    ) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEvent(accountId, recurrenceId);
        validateEventTimeRange(request.startAt(), request.endAt());
        validateOriginStartAt(recurrenceEvent, request.originStartAt());

        RecurrenceEventOverride override = findOrCreateModifiedOverride(
                recurrenceEvent,
                request.originStartAt(),
                request.startAt(),
                request.endAt()
        );
        if (override.isDeleted()) {
            throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
        }

        return EventResponse.recurrenceOccurrence(
                recurrenceEvent,
                override.getOriginStartAt(),
                override.getOverrideStartAt(),
                override.getOverrideEndAt()
        );
    }

    @Transactional
    public void deleteRecurrenceEvent(Long accountId, Long recurrenceId) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEvent(accountId, recurrenceId);
        recurrenceEventOverrideRepository.deleteByRecurrenceEvent_Id(recurrenceId);
        eventRepository.deleteAll(
                eventRepository.findByRecurrenceIdAndAccount_IdOrderByStartAtAsc(recurrenceId, accountId)
        );
        recurrenceEventRepository.delete(recurrenceEvent);
    }

    @Transactional
    public void deleteRecurrenceOccurrence(Long accountId, Long recurrenceId, Instant originStartAt) {
        RecurrenceEvent recurrenceEvent = findRecurrenceEvent(accountId, recurrenceId);
        validateOriginStartAt(recurrenceEvent, originStartAt);

        RecurrenceEventOverride override = recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, originStartAt)
                .orElse(null);
        if (override == null) {
            recurrenceEventOverrideRepository.save(
                    RecurrenceEventOverride.deleted(recurrenceEvent, originStartAt, Instant.now())
            );
            recurrenceEventOverrideRepository.flush();
            return;
        }

        override.markDeleted(Instant.now());
        recurrenceEventOverrideRepository.flush();
    }

    private RecurrenceEvent findRecurrenceEvent(Long accountId, Long recurrenceId) {
        return recurrenceEventRepository.findByIdAndAccount_Id(recurrenceId, accountId)
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

        throw new CalioException(ErrorCode.INVALID_RECURRENCE_TIME_RANGE);
    }

    private void validateEventTimeRange(Instant startAt, Instant endAt) {
        if (startAt.isBefore(endAt)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }

    private RecurrenceEventOverride findOrCreateModifiedOverride(
            RecurrenceEvent recurrenceEvent,
            Instant originStartAt,
            Instant overrideStartAt,
            Instant overrideEndAt
    ) {
        return recurrenceEventOverrideRepository
                .findByRecurrenceEvent_IdAndOriginStartAt(recurrenceEvent.getId(), originStartAt)
                .map(override -> updateModifiedOverride(override, overrideStartAt, overrideEndAt))
                .orElseGet(() -> saveModifiedOverride(recurrenceEvent, originStartAt, overrideStartAt, overrideEndAt));
    }

    private RecurrenceEventOverride updateModifiedOverride(
            RecurrenceEventOverride override,
            Instant overrideStartAt,
            Instant overrideEndAt
    ) {
        if (override.isDeleted()) {
            return override;
        }

        override.changeModifiedTime(overrideStartAt, overrideEndAt);
        recurrenceEventOverrideRepository.flush();
        return override;
    }

    private RecurrenceEventOverride saveModifiedOverride(
            RecurrenceEvent recurrenceEvent,
            Instant originStartAt,
            Instant overrideStartAt,
            Instant overrideEndAt
    ) {
        try {
            RecurrenceEventOverride override = recurrenceEventOverrideRepository.save(
                    RecurrenceEventOverride.modified(recurrenceEvent, originStartAt, overrideStartAt, overrideEndAt)
            );
            recurrenceEventOverrideRepository.flush();
            return override;
        } catch (DuplicateKeyException exception) {
            return findExistingModifiedOverride(recurrenceEvent.getId(), originStartAt, overrideStartAt, overrideEndAt);
        } catch (DataIntegrityViolationException exception) {
            return findExistingModifiedOverride(recurrenceEvent.getId(), originStartAt, overrideStartAt, overrideEndAt);
        }
    }

    private RecurrenceEventOverride findExistingModifiedOverride(
            Long recurrenceId,
            Instant originStartAt,
            Instant overrideStartAt,
            Instant overrideEndAt
    ) {
        return recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, originStartAt)
                .map(override -> updateModifiedOverride(override, overrideStartAt, overrideEndAt))
                .orElseThrow(() -> new CalioException(ErrorCode.INTERNAL_SERVER_ERROR));
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

    private void validateOriginStartAt(RecurrenceEvent recurrenceEvent, Instant originStartAt) {
        if (isOriginStartAtGeneratable(recurrenceEvent, originStartAt)) {
            return;
        }

        throw new CalioException(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
    }

    private boolean isOriginStartAtGeneratable(RecurrenceEvent recurrenceEvent, Instant originStartAt) {
        int intervalIndex = 0;
        LocalDate occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);

        while (isOccurrenceStartBeforeRuleEnd(recurrenceEvent, occurrenceDate)) {
            if (toInstant(occurrenceDate, recurrenceEvent.getRecurrenceStartTime()).equals(originStartAt)) {
                return true;
            }
            intervalIndex++;
            occurrenceDate = occurrenceDate(recurrenceEvent, intervalIndex);
        }

        return false;
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
