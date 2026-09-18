package com.calio.calendar.holiday.domain;

import java.time.LocalDate;

public record NationalHolidayContent(LocalDate holidayDate, String holidayTitle) {

  public static NationalHolidayContent from(NationalHoliday holiday) {
    return new NationalHolidayContent(holiday.getHolidayDate(), holiday.getHolidayTitle());
  }
}
