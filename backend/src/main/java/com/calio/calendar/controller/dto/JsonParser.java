package com.calio.calendar.controller.dto;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class JsonParser {

    public String parseText(JsonNode body, String fieldName) {
        JsonNode value = body.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        if (!value.isTextual()) {
            throw new CalioException(ErrorCode.INVALID_FIELD_TYPE, fieldName);
        }

        return value.asText();
    }

    public LocalDate parseLocalDate(JsonNode body, String fieldName) {
        String text = parseText(body, fieldName);
        if (text == null) {
            return null;
        }

        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException exception) {
            throw new CalioException(ErrorCode.INVALID_DATE_FORMAT, fieldName);
        }
    }

    public LocalTime parseLocalTime(JsonNode body, String fieldName) {
        String text = parseText(body, fieldName);
        if (text == null) {
            return null;
        }

        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException exception) {
            throw new CalioException(ErrorCode.INVALID_TIME_FORMAT, fieldName);
        }
    }

    public Instant parseInstant(JsonNode body, String fieldName) {
        String text = parseText(body, fieldName);
        if (text == null) {
            return null;
        }

        try {
            return Instant.parse(text);
        } catch (DateTimeParseException exception) {
            throw new CalioException(ErrorCode.INVALID_TIMESTAMP_FORMAT, fieldName);
        }
    }
}
