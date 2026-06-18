package com.calio.calendar.controller.dto;

import java.util.Set;

public record UpdateRecurrenceEventCommand(
        UpdateType updateType,
        UpdateRecurrenceEventRequest request,
        Set<String> presentFields
) {

    public boolean hasField(String fieldName) {
        return presentFields.contains(fieldName);
    }
}
