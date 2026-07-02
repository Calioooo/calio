package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.NationalHoliday;
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
