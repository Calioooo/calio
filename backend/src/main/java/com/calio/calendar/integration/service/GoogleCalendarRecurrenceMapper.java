package com.calio.calendar.integration.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer.NormalizedEventSchedule;
import com.calio.calendar.external.google.dto.GoogleCalendarEventItem;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GoogleCalendarRecurrenceMapper {

    private static final String UNTITLED_EVENT_TITLE = "(제목 없음)";

    private final GoogleCalendarEventTimeNormalizer timeNormalizer;
    private final Rfc5545RecurrenceEngine recurrenceEngine;

    public GoogleCalendarRecurrenceMapper(
            GoogleCalendarEventTimeNormalizer timeNormalizer,
            Rfc5545RecurrenceEngine recurrenceEngine
    ) {
        this.timeNormalizer = timeNormalizer;
        this.recurrenceEngine = recurrenceEngine;
    }

    public MasterResult mapMaster(GoogleCalendarEventItem item) {
        requireMaster(item);
        NormalizedEventSchedule schedule = timeNormalizer.normalizeSchedule(item.start(), item.end());
        RecurrenceSchedule recurrenceSchedule = recurrenceSchedule(schedule);
        List<String> recurrenceRules = validateRecurrence(recurrenceSchedule, item.recurrence());
        return new MasterResult(
                item.id(),
                item.etag(),
                item.updatedAt(),
                canonicalTitle(item.summary()),
                item.description(),
                schedule,
                recurrenceRules
        );
    }

    public ExceptionResult mapException(GoogleCalendarEventItem item) {
        requireExceptionIdentity(item);
        Instant originStartAt = timeNormalizer.normalize(item.originalStartTime()).instant();
        if (item.isCancelled()) {
            return new CancelledExceptionResult(
                    item.id(),
                    item.recurringEventId(),
                    originStartAt,
                    item.etag(),
                    item.updatedAt()
            );
        }
        NormalizedEventSchedule schedule = timeNormalizer.normalizeSchedule(item.start(), item.end());
        return new ActiveExceptionResult(
                item.id(),
                item.recurringEventId(),
                originStartAt,
                item.etag(),
                item.updatedAt(),
                canonicalTitle(item.summary()),
                item.description(),
                schedule
        );
    }

    private RecurrenceSchedule recurrenceSchedule(NormalizedEventSchedule schedule) {
        try {
            return RecurrenceSchedule.create(
                    schedule.allDay(),
                    schedule.startAt(),
                    schedule.endAt(),
                    schedule.timeZone()
            );
        } catch (CalioException exception) {
            throw translateKnownProviderFailure(exception);
        }
    }

    private List<String> validateRecurrence(
            RecurrenceSchedule schedule,
            List<String> recurrenceRules
    ) {
        try {
            return recurrenceEngine.validate(schedule, recurrenceRules);
        } catch (CalioException exception) {
            throw translateKnownProviderFailure(exception);
        }
    }

    private RuntimeException translateKnownProviderFailure(CalioException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        if (errorCode == ErrorCode.INVALID_RECURRENCE_RULE
                || errorCode == ErrorCode.INVALID_RECURRENCE_SCHEDULE
                || errorCode == ErrorCode.INVALID_TIME_ZONE
                || errorCode == ErrorCode.RECURRENCE_OCCURRENCE_LIMIT_EXCEEDED) {
            return new CalioException(
                    ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID,
                    exception
            );
        }
        return exception;
    }

    private void requireMaster(GoogleCalendarEventItem item) {
        boolean isInvalid = item == null
                || item.isCancelled()
                || !item.isRecurrenceMaster()
                || item.isRecurrenceOccurrence();
        if (isInvalid) {
            throw invalidResponse();
        }
    }

    private void requireExceptionIdentity(GoogleCalendarEventItem item) {
        boolean isInvalid = item == null
                || !hasText(item.id())
                || !hasText(item.recurringEventId())
                || item.originalStartTime() == null;
        if (isInvalid) {
            throw invalidResponse();
        }
    }

    private String canonicalTitle(String summary) {
        return summary == null || summary.isBlank() ? UNTITLED_EVENT_TITLE : summary;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private CalioException invalidResponse() {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
    }

    public record MasterResult(
            String externalEventId,
            String providerEtag,
            Instant providerUpdatedAt,
            String title,
            String description,
            NormalizedEventSchedule schedule,
            List<String> recurrenceRules
    ) {

        public MasterResult {
            recurrenceRules = List.copyOf(recurrenceRules);
        }
    }

    public sealed interface ExceptionResult
            permits ActiveExceptionResult, CancelledExceptionResult {

        String externalEventId();

        String parentExternalEventId();

        Instant originStartAt();

        String providerEtag();

        Instant providerUpdatedAt();
    }

    public record ActiveExceptionResult(
            String externalEventId,
            String parentExternalEventId,
            Instant originStartAt,
            String providerEtag,
            Instant providerUpdatedAt,
            String title,
            String description,
            NormalizedEventSchedule schedule
    ) implements ExceptionResult {
    }

    public record CancelledExceptionResult(
            String externalEventId,
            String parentExternalEventId,
            Instant originStartAt,
            String providerEtag,
            Instant providerUpdatedAt
    ) implements ExceptionResult {
    }
}
