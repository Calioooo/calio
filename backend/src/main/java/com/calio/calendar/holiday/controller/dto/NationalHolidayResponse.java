package com.calio.calendar.holiday.controller.dto;

import com.calio.calendar.holiday.domain.NationalHoliday;
import java.time.LocalDate;

public record NationalHolidayResponse(
        Long nationalHolidayId,
        LocalDate holidayDate,
        String holidayTitle
) {

    public static NationalHolidayResponse from(NationalHoliday nationalHoliday) {
        return new NationalHolidayResponse(
                nationalHoliday.getNationalHolidayId(),
                nationalHoliday.getHolidayDate(),
                nationalHoliday.getHolidayTitle()
        );
    }
}
