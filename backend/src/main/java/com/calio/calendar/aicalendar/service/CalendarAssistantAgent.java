package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.service.dto.CalendarAssistantRequest;

public interface CalendarAssistantAgent {

    String answer(CalendarAssistantRequest request);
}
