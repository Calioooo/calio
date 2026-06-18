package com.calio.calendar.controller.dto;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class UpdateRecurrenceEventValidator {

    private static final String RECURRENCE_EVENT_SCOPE = "RECURRENCE_EVENT";
    private static final String SINGLE_OCCURRENCE_SCOPE = "SINGLE_OCCURRENCE";
    private static final Set<String> WHOLE_FIELDS = Set.of(
            "recurrenceTitle",
            "recurrenceDescription",
            "recurrenceStartDate",
            "recurrenceEndDate",
            "recurrenceStartTime",
            "recurrenceEndTime",
            "recurrenceFrequency"
    );
    private static final Set<String> OCCURRENCE_FIELDS = Set.of(
            "targetOccurrenceStartAt",
            "modifiedStartAt",
            "modifiedEndAt"
    );
    private static final Set<String> NON_NULL_WHOLE_FIELDS = Set.of(
            "recurrenceTitle",
            "recurrenceStartDate",
            "recurrenceEndDate",
            "recurrenceStartTime",
            "recurrenceEndTime",
            "recurrenceFrequency"
    );
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "recurrenceTitle",
            "recurrenceDescription",
            "recurrenceStartDate",
            "recurrenceEndDate",
            "recurrenceStartTime",
            "recurrenceEndTime",
            "recurrenceFrequency",
            "targetOccurrenceStartAt",
            "modifiedStartAt",
            "modifiedEndAt"
    );

    private final JsonParser jsonParser;

    public UpdateRecurrenceEventValidator(JsonParser jsonParser) {
        this.jsonParser = jsonParser;
    }

    public void validate(JsonNode body, String updateScope) {
        validateUpdateScope(updateScope);
        validateBodyObject(body);
        validateAllowedFields(body);
        rejectBodyUpdateScope(body);
        rejectMixedFieldGroups(body);
        validateScopeFields(body, updateScope);
        validateNonNullableWholeFields(body);
        validateBlankStrings(body);
        validateDateTimeFormats(body);
    }

    private void validateUpdateScope(String updateScope) {
        if (RECURRENCE_EVENT_SCOPE.equals(updateScope) || SINGLE_OCCURRENCE_SCOPE.equals(updateScope)) {
            return;
        }

        throwValidationFailed();
    }

    private void validateBodyObject(JsonNode body) {
        if (body != null && body.isObject()) {
            return;
        }

        throwValidationFailed();
    }

    private void validateAllowedFields(JsonNode body) {
        for (String fieldName : body.propertyNames()) {
            if (!ALLOWED_FIELDS.contains(fieldName) && !"updateScope".equals(fieldName)) {
                throwValidationFailed();
            }
        }
    }

    private void rejectBodyUpdateScope(JsonNode body) {
        if (!body.has("updateScope")) {
            return;
        }

        throwValidationFailed();
    }

    private void rejectMixedFieldGroups(JsonNode body) {
        if (hasAny(body, WHOLE_FIELDS) && hasAny(body, OCCURRENCE_FIELDS)) {
            throwValidationFailed();
        }
    }

    private void validateScopeFields(JsonNode body, String updateScope) {
        if (RECURRENCE_EVENT_SCOPE.equals(updateScope)) {
            rejectOccurrenceFields(body);
            return;
        }

        rejectWholeFields(body);
        requireOccurrenceFields(body);
    }

    private void rejectOccurrenceFields(JsonNode body) {
        if (hasAny(body, OCCURRENCE_FIELDS)) {
            throwValidationFailed();
        }
    }

    private void rejectWholeFields(JsonNode body) {
        if (hasAny(body, WHOLE_FIELDS)) {
            throwValidationFailed();
        }
    }

    private void requireOccurrenceFields(JsonNode body) {
        for (String fieldName : OCCURRENCE_FIELDS) {
            if (!body.has(fieldName) || body.get(fieldName).isNull()) {
                throwValidationFailed();
            }
        }
    }

    private void validateNonNullableWholeFields(JsonNode body) {
        for (String fieldName : NON_NULL_WHOLE_FIELDS) {
            if (body.has(fieldName) && body.get(fieldName).isNull()) {
                throwValidationFailed();
            }
        }
    }

    private void validateBlankStrings(JsonNode body) {
        for (String fieldName : body.propertyNames()) {
            JsonNode value = body.get(fieldName);
            if (value != null && value.isTextual() && value.asText().isBlank()) {
                throwValidationFailed();
            }
        }
    }

    private void validateDateTimeFormats(JsonNode body) {
        jsonParser.parseLocalDate(body, "recurrenceStartDate");
        jsonParser.parseLocalDate(body, "recurrenceEndDate");
        jsonParser.parseLocalTime(body, "recurrenceStartTime");
        jsonParser.parseLocalTime(body, "recurrenceEndTime");
        jsonParser.parseInstant(body, "targetOccurrenceStartAt");
        jsonParser.parseInstant(body, "modifiedStartAt");
        jsonParser.parseInstant(body, "modifiedEndAt");
    }

    private boolean hasAny(JsonNode body, Set<String> fieldNames) {
        for (String fieldName : fieldNames) {
            if (body.has(fieldName)) {
                return true;
            }
        }

        return false;
    }

    private void throwValidationFailed() {
        throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
}
