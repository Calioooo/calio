package com.calio.calendar.service;

import com.calio.calendar.repository.entity.NationalHoliday;
import java.time.LocalDate;

record NationalHolidayProviderRow(LocalDate holidayDate, String holidayTitle) {

    static NationalHolidayProviderRow from(NationalHoliday holiday) {
        return new NationalHolidayProviderRow(holiday.getHolidayDate(), holiday.getHolidayTitle());
    }
}
