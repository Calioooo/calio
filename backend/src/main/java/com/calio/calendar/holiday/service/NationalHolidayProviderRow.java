package com.calio.calendar.holiday.service;

import com.calio.calendar.holiday.domain.NationalHoliday;
import java.time.LocalDate;

record NationalHolidayProviderRow(LocalDate holidayDate, String holidayTitle) {

    static NationalHolidayProviderRow from(NationalHoliday holiday) {
        return new NationalHolidayProviderRow(holiday.getHolidayDate(), holiday.getHolidayTitle());
    }
}
