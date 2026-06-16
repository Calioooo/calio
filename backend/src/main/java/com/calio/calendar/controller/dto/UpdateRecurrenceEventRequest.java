package com.calio.calendar.controller.dto;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import tools.jackson.databind.JsonNode;

public record UpdateRecurrenceEventRequest(
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

    private static final Set<String> WHOLE_UPDATE_FIELDS = Set.of(
            "recurrenceTitle",
            "recurrenceDescription",
            "recurrenceStartDate",
            "recurrenceEndDate",
            "recurrenceStartTime",
            "recurrenceEndTime",
            "recurrenceFrequency"
    );
    private static final Set<String> SINGLE_OCCURRENCE_FIELDS = Set.of(
            "targetOccurrenceStartAt",
            "modifiedStartAt",
            "modifiedEndAt"
    );

    public static UpdateRecurrenceEventRequest from(JsonNode body) {
        validateObjectBody(body);
        validateNoBodyUpdateScope(body);
        validateNoMixedFields(body);

        return new UpdateRecurrenceEventRequest(
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

    public void validateWholeUpdateScope() {
        if (hasAnySingleOccurrenceField()) {
            throwValidationFailed();
        }
    }

    public void validateSingleOccurrenceScope() {
        if (hasAnyWholeUpdateField() || missesSingleOccurrenceRequiredFields()) {
            throwValidationFailed();
        }

        if (modifiedStartAt.isBefore(modifiedEndAt)) {
            return;
        }

        throwValidationFailed();
    }

    private static void validateObjectBody(JsonNode body) {
        if (body != null && body.isObject()) {
            return;
        }

        throwValidationFailed();
    }

    private static void validateNoBodyUpdateScope(JsonNode body) {
        if (!body.has("updateScope")) {
            return;
        }

        throwValidationFailed();
    }

    private static void validateNoMixedFields(JsonNode body) {
        boolean hasWholeUpdateField = containsAny(body, WHOLE_UPDATE_FIELDS);
        boolean hasSingleOccurrenceField = containsAny(body, SINGLE_OCCURRENCE_FIELDS);

        if (!hasWholeUpdateField || !hasSingleOccurrenceField) {
            return;
        }

        throwValidationFailed();
    }

    private static boolean containsAny(JsonNode body, Set<String> fields) {
        return fields.stream().anyMatch(body::has);
    }

    private static String parseText(JsonNode body, String fieldName) {
        if (!body.has(fieldName)) {
            return null;
        }

        if (!body.get(fieldName).isNull()) {
            String value = body.get(fieldName).asText();
            validateNotBlank(fieldName, value);
            return value;
        }

        throwValidationFailed();
        return null;
    }

    private static String parseNullableText(JsonNode body, String fieldName) {
        if (!body.has(fieldName) || body.get(fieldName).isNull()) {
            return null;
        }

        return body.get(fieldName).asText();
    }

    private static void validateNotBlank(String fieldName, String value) {
        if (!"recurrenceTitle".equals(fieldName) || !value.isBlank()) {
            return;
        }

        throwValidationFailed();
    }

    private static LocalDate parseLocalDate(JsonNode body, String fieldName) {
        if (!body.has(fieldName)) {
            return null;
        }

        if (body.get(fieldName).isNull()) {
            throwValidationFailed();
        }

        try {
            return LocalDate.parse(body.get(fieldName).asText());
        } catch (DateTimeParseException exception) {
            throwValidationFailed();
            return null;
        }
    }

    private static LocalTime parseLocalTime(JsonNode body, String fieldName) {
        if (!body.has(fieldName)) {
            return null;
        }

        if (body.get(fieldName).isNull()) {
            throwValidationFailed();
        }

        try {
            return LocalTime.parse(body.get(fieldName).asText());
        } catch (DateTimeParseException exception) {
            throwValidationFailed();
            return null;
        }
    }

    private static Instant parseInstant(JsonNode body, String fieldName) {
        if (!body.has(fieldName)) {
            return null;
        }

        if (body.get(fieldName).isNull()) {
            throwValidationFailed();
        }

        try {
            return Instant.parse(body.get(fieldName).asText());
        } catch (DateTimeParseException exception) {
            throwValidationFailed();
            return null;
        }
    }

    private boolean hasAnyWholeUpdateField() {
        return hasRecurrenceTitle
                || hasRecurrenceDescription
                || hasRecurrenceStartDate
                || hasRecurrenceEndDate
                || hasRecurrenceStartTime
                || hasRecurrenceEndTime
                || hasRecurrenceFrequency;
    }

    private boolean hasAnySingleOccurrenceField() {
        return hasTargetOccurrenceStartAt || hasModifiedStartAt || hasModifiedEndAt;
    }

    private boolean missesSingleOccurrenceRequiredFields() {
        return !hasTargetOccurrenceStartAt || !hasModifiedStartAt || !hasModifiedEndAt;
    }

    private static void throwValidationFailed() {
        throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
}
