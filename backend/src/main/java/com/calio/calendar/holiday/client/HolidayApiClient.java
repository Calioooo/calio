package com.calio.calendar.holiday.client;

import com.calio.calendar.holiday.domain.NationalHolidayContent;
import java.util.List;

public interface HolidayApiClient {

  List<NationalHolidayContent> fetchHolidays(int year);
}
