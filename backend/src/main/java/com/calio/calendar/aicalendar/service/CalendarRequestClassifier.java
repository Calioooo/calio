package com.calio.calendar.aicalendar.service;

import com.calio.calendar.aicalendar.service.dto.CalendarAssistantRequest;

public interface CalendarRequestClassifier {

    CalendarRequestCategory classify(CalendarAssistantRequest request);
}
