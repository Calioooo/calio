package com.calio.calendar.holiday.usecase;

import com.calio.calendar.holiday.usecase.dto.NationalHolidayContent;
import java.util.List;

public interface HolidayApiClient {

  List<NationalHolidayContent> fetchHolidays(int year);
}
