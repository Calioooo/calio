package com.calio.calendar.controller.dto;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import tools.jackson.databind.JsonNode;

public record UpdateRecurrenceEventRequest(
        boolean hasBodyUpdateScope,
        String recurrenceTitle,
        boolean hasRecurrenceTitle,
        String recurrenceDescription,
        boolean hasRecurrenceDescription,
        LocalDate recurrenceStartDate,
        boolean hasRecurrenceStartDate,
        LocalDate recurrenceEndDate,
        boolean hasRecurrenceEndDate,
        LocalTime recurrenceStartTime,
        boolean hasRecurrenceStartTime,
        LocalTime recurrenceEndTime,
        boolean hasRecurrenceEndTime,
        String recurrenceFrequency,
        boolean hasRecurrenceFrequency,
        Instant targetOccurrenceStartAt,
        boolean hasTargetOccurrenceStartAt,
        Instant modifiedStartAt,
        boolean hasModifiedStartAt,
        Instant modifiedEndAt,
        boolean hasModifiedEndAt
) {

    public static UpdateRecurrenceEventRequest from(JsonNode body) {
        validateObjectBody(body);

        return new UpdateRecurrenceEventRequest(
                body.has("updateScope"),
                parseText(body, "recurrenceTitle"),
                body.has("recurrenceTitle"),
                parseNullableText(body, "recurrenceDescription"),
                body.has("recurrenceDescription"),
                parseLocalDate(body, "recurrenceStartDate"),
                body.has("recurrenceStartDate"),
                parseLocalDate(body, "recurrenceEndDate"),
                body.has("recurrenceEndDate"),
                parseLocalTime(body, "recurrenceStartTime"),
                body.has("recurrenceStartTime"),
                parseLocalTime(body, "recurrenceEndTime"),
                body.has("recurrenceEndTime"),
                parseText(body, "recurrenceFrequency"),
                body.has("recurrenceFrequency"),
                parseInstant(body, "targetOccurrenceStartAt"),
                body.has("targetOccurrenceStartAt"),
                parseInstant(body, "modifiedStartAt"),
                body.has("modifiedStartAt"),
                parseInstant(body, "modifiedEndAt"),
                body.has("modifiedEndAt")
        );
    }

    private static void validateObjectBody(JsonNode body) {
        if (body != null && body.isObject()) {
            return;
        }

        throwValidationFailed();
    }

    private static String parseText(JsonNode body, String fieldName) {
        if (!body.has(fieldName) || body.get(fieldName).isNull()) {
            return null;
        }

        return body.get(fieldName).asText();
    }

    private static String parseNullableText(JsonNode body, String fieldName) {
        if (!body.has(fieldName) || body.get(fieldName).isNull()) {
            return null;
        }

        return body.get(fieldName).asText();
    }

    private static LocalDate parseLocalDate(JsonNode body, String fieldName) {
        if (!body.has(fieldName) || body.get(fieldName).isNull()) {
            return null;
        }

        try {
            return LocalDate.parse(body.get(fieldName).asText());
        } catch (DateTimeParseException exception) {
            throwValidationFailed();
            return null;
        }
    }

    private static LocalTime parseLocalTime(JsonNode body, String fieldName) {
        if (!body.has(fieldName) || body.get(fieldName).isNull()) {
            return null;
        }

        try {
            return LocalTime.parse(body.get(fieldName).asText());
        } catch (DateTimeParseException exception) {
            throwValidationFailed();
            return null;
        }
    }

    private static Instant parseInstant(JsonNode body, String fieldName) {
        if (!body.has(fieldName) || body.get(fieldName).isNull()) {
            return null;
        }

        try {
            return Instant.parse(body.get(fieldName).asText());
        } catch (DateTimeParseException exception) {
            throwValidationFailed();
            return null;
        }
    }

    private static void throwValidationFailed() {
        throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
}
