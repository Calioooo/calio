package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.Event;
import com.calio.calendar.repository.entity.NationalHoliday;

public record CalendarItemResponse(
        CalendarItemType itemType,
        EventResponse event,
        NationalHolidayResponse nationalHoliday
) {

    public static CalendarItemResponse event(Event event) {
        return new CalendarItemResponse(
                CalendarItemType.EVENT,
                EventResponse.from(event),
                null
        );
    }

    public static CalendarItemResponse nationalHoliday(NationalHoliday nationalHoliday) {
        return new CalendarItemResponse(
                CalendarItemType.NATIONAL_HOLIDAY,
                null,
                NationalHolidayResponse.from(nationalHoliday)
        );
    }
}
