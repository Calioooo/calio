package com.calio.calendar.groupcalendar.controller.dto;

import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceOccurrence;
import com.calio.calendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import java.time.Instant;

public record SharedCalendarItemResponse(
        String publicItemId,
        String title,
        String description,
        Instant startAt,
        Instant endAt,
        boolean allDay,
        String timeZone,
        boolean isRecurrenceOccurrence,
        Instant originStartAt
) {

    private static final String ANONYMOUS_TITLE = "익명 일정";

    public static SharedCalendarItemResponse from(PersonalEventGroupShare share) {
        var event = share.getEvent();
        boolean anonymous = share.isAnonymous();
        return new SharedCalendarItemResponse(
                "shared-event:" + share.getPublicShareId(),
                anonymous ? ANONYMOUS_TITLE : event.getTitle(),
                anonymous ? null : event.getDescription(),
                event.getStartAt(),
                event.getEndAt(),
                event.isAllDay(),
                event.getTimeZone(),
                false,
                null
        );
    }

    public static SharedCalendarItemResponse recurrenceOccurrence(
            PersonalRecurrenceGroupShare share,
            RecurrenceOccurrence occurrence,
            RecurrenceEventOverride override
    ) {
        var recurrenceEvent = share.getRecurrenceEvent();
        boolean anonymous = share.isAnonymous();
        boolean overridden = override != null;
        return new SharedCalendarItemResponse(
                "shared-recurrence:" + share.getPublicShareId() + ":" + occurrence.originStartAt(),
                anonymous ? ANONYMOUS_TITLE : overridden ? override.getOverrideTitle() : recurrenceEvent.getTitle(),
                anonymous ? null : overridden ? override.getOverrideDescription() : recurrenceEvent.getDescription(),
                overridden ? override.getOverrideStartAt() : occurrence.startAt(),
                overridden ? override.getOverrideEndAt() : occurrence.endAt(),
                overridden ? override.isOverrideAllDay() : recurrenceEvent.isAllDay(),
                overridden ? override.getOverrideTimeZone() : recurrenceEvent.getTimeZone(),
                true,
                occurrence.originStartAt()
        );
    }
}
