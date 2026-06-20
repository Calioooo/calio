package com.calio.calendar.controller.dto;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.entity.RecurrenceFrequency;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class UpdateRecurrenceEventValidator {

    private static final Set<String> SUPPORTED_FIELDS = Set.of(
            "title",
            "description",
            "startAt",
            "endAt",
            "recurrenceFrequency"
    );

    public void validate(JsonNode requestBody) {
        validateObjectBody(requestBody);
        validateSupportedFields(requestBody);
        validateTitle(requestBody);
        validateInstantField(
                requestBody,
                "startAt",
                ErrorCode.RECURRENCE_UPDATE_START_AT_REQUIRED,
                ErrorCode.RECURRENCE_UPDATE_START_AT_INVALID
        );
        validateInstantField(
                requestBody,
                "endAt",
                ErrorCode.RECURRENCE_UPDATE_END_AT_REQUIRED,
                ErrorCode.RECURRENCE_UPDATE_END_AT_INVALID
        );
        validateFrequency(requestBody);
    }

    private void validateObjectBody(JsonNode requestBody) {
        if (requestBody != null && requestBody.isObject()) {
            return;
        }

        throw new CalioException(ErrorCode.RECURRENCE_UPDATE_BODY_NOT_OBJECT);
    }

    private void validateSupportedFields(JsonNode requestBody) {
        for (String fieldName : requestBody.propertyNames()) {
            if ("updateScope".equals(fieldName)) {
                throw new CalioException(ErrorCode.RECURRENCE_UPDATE_SCOPE_UNSUPPORTED);
            }

            if (!SUPPORTED_FIELDS.contains(fieldName)) {
                throw new CalioException(ErrorCode.RECURRENCE_UPDATE_UNSUPPORTED_FIELD);
            }
        }
    }

    private void validateTitle(JsonNode requestBody) {
        if (!requestBody.hasNonNull("title")) {
            return;
        }

        if (!requestBody.get("title").asString().isBlank()) {
            return;
        }

        throw new CalioException(ErrorCode.RECURRENCE_UPDATE_TITLE_BLANK);
    }

    private void validateInstantField(
            JsonNode requestBody,
            String fieldName,
            ErrorCode requiredErrorCode,
            ErrorCode invalidErrorCode
    ) {
        if (!requestBody.hasNonNull(fieldName)) {
            throw new CalioException(requiredErrorCode);
        }

        try {
            Instant.parse(requestBody.get(fieldName).asString());
        } catch (DateTimeParseException exception) {
            throw new CalioException(invalidErrorCode);
        }
    }

    private void validateFrequency(JsonNode requestBody) {
        if (!requestBody.hasNonNull("recurrenceFrequency")) {
            throw new CalioException(ErrorCode.RECURRENCE_UPDATE_FREQUENCY_REQUIRED);
        }

        try {
            RecurrenceFrequency.valueOf(requestBody.get("recurrenceFrequency").asString());
        } catch (IllegalArgumentException exception) {
            throw new CalioException(ErrorCode.RECURRENCE_UPDATE_FREQUENCY_INVALID);
        }
    }
}
