package com.calio.calendar.holiday.domain;

import java.time.LocalDate;

public record NationalHolidayContent(LocalDate holidayDate, String holidayTitle)
    implements Comparable<NationalHolidayContent> {

  public static NationalHolidayContent from(NationalHoliday holiday) {
    return new NationalHolidayContent(holiday.getHolidayDate(), holiday.getHolidayTitle());
  }

  @Override
  public int compareTo(NationalHolidayContent other) {
    int dateComparison = holidayDate.compareTo(other.holidayDate());
    if (dateComparison != 0) {
      return dateComparison;
    }
    return holidayTitle.compareTo(other.holidayTitle());
  }
}
