package com.calio.calendar.controller.dto;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangeImportantEventRequest(
        @JsonProperty("isImportantEvent") JsonNode isImportantEvent
) {

    public boolean importantEventValue() {
        if (isImportantEvent == null || !isImportantEvent.isBoolean()) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }

        return isImportantEvent.asBoolean();
    }
}
