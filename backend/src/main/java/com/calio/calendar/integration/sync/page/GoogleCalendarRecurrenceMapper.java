package com.calio.calendar.integration.sync.page;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventTimeNormalizer;
import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.ActiveRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.CancelledRecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
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

    public RecurrenceEventUpsert mapRecurrenceEvent(GoogleCalendarEventResponse item) {
        validateRecurrenceEvent(item);
        NormalizedEventSchedule schedule = timeNormalizer.normalizeSchedule(item.start(), item.end());
        RecurrenceSchedule recurrenceSchedule = toRecurrenceSchedule(schedule);
        List<String> recurrenceRules = validateRecurrenceRules(recurrenceSchedule, item.recurrence());
        return new RecurrenceEventUpsert(
                item.id(),
                requireProviderEtag(item),
                eventTitle(item.summary()),
                item.description(),
                schedule,
                recurrenceRules
        );
    }

    public RecurrenceEventOverrideUpsert mapRecurrenceOverride(GoogleCalendarEventResponse item) {
        validateRecurrenceEventOverride(item);
        Instant originStartAt = timeNormalizer.normalize(item.originalStartTime()).instant();
        if (item.isCancelled()) {
            return new CancelledRecurrenceEventOverrideUpsert(
                    item.id(),
                    item.recurringEventId(),
                    originStartAt,
                    requireProviderEtag(item),
                    item.updatedAt() == null ? originStartAt : item.updatedAt()
            );
        }
        NormalizedEventSchedule schedule = timeNormalizer.normalizeSchedule(item.start(), item.end());
        return new ActiveRecurrenceEventOverrideUpsert(
                item.id(),
                item.recurringEventId(),
                originStartAt,
                requireProviderEtag(item),
                eventTitle(item.summary()),
                item.description(),
                schedule
        );
    }

    private RecurrenceSchedule toRecurrenceSchedule(NormalizedEventSchedule schedule) {
        try {
            return RecurrenceSchedule.create(
                    schedule.allDay(),
                    schedule.startAt(),
                    schedule.endAt(),
                    schedule.timeZone()
            );
        } catch (CalioException exception) {
            throw translateKnownGoogleCalendarFailure(exception);
        }
    }

    private List<String> validateRecurrenceRules(
            RecurrenceSchedule schedule,
            List<String> recurrenceRules
    ) {
        try {
            return recurrenceEngine.validate(schedule, recurrenceRules);
        } catch (CalioException exception) {
            throw translateKnownGoogleCalendarFailure(exception);
        }
    }

    private RuntimeException translateKnownGoogleCalendarFailure(CalioException exception) {
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

    private void validateRecurrenceEvent(GoogleCalendarEventResponse item) {
        boolean isInvalid = item == null
                || item.isCancelled()
                || !item.isRecurrenceEvent()
                || item.isRecurrenceOverride();
        if (isInvalid) {
            throw invalidResponse();
        }
    }

    private void validateRecurrenceEventOverride(GoogleCalendarEventResponse item) {
        boolean isInvalid = item == null
                || !hasText(item.id())
                || !hasText(item.recurringEventId())
                || !hasText(item.etag())
                || item.originalStartTime() == null
                || item.isRecurrenceEvent();
        if (isInvalid) {
            throw invalidResponse();
        }
    }

    private String eventTitle(String summary) {
        return summary == null || summary.isBlank() ? UNTITLED_EVENT_TITLE : summary;
    }

    private String requireProviderEtag(GoogleCalendarEventResponse item) {
        if (!hasText(item.etag())) {
            throw invalidResponse();
        }
        return item.etag();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private CalioException invalidResponse() {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
    }

}
