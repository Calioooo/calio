package com.calio.calendar.aicalendar.controller.dto;

import com.calio.calendar.aicalendar.domain.CalendarMutationScope;
import com.calio.calendar.aicalendar.domain.CalendarMutationType;
import com.calio.calendar.aicalendar.service.dto.CalendarMutationPreview;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.tag.controller.dto.TagResponse;
import java.time.Instant;

public record CalendarMutationPreviewResponse(
        CalendarMutationType type,
        CalendarMutationScope scope,
        CalendarMutationEventResponse before,
        CalendarMutationEventResponse after
) {

    public static CalendarMutationPreviewResponse from(CalendarMutationPreview preview) {
        return new CalendarMutationPreviewResponse(
                preview.type(),
                preview.scope(),
                CalendarMutationEventResponse.from(preview.before()),
                CalendarMutationEventResponse.from(preview.after())
        );
    }

    public record CalendarMutationEventResponse(
            String title,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            TagResponse tag
    ) {

        private static CalendarMutationEventResponse from(EventResponse event) {
            if (event == null) {
                return null;
            }
            return new CalendarMutationEventResponse(
                    event.title(),
                    event.startAt(),
                    event.endAt(),
                    event.allDay(),
                    event.tag()
            );
        }
    }
}
