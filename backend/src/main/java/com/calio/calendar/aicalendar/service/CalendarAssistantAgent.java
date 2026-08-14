package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.service.dto.CalendarAssistantRequest;
import com.calio.calendar.aicalendar.service.dto.CalendarAssistantAnswer;

public interface CalendarAssistantAgent {

    CalendarAssistantAnswer answer(CalendarAssistantRequest request);
}
