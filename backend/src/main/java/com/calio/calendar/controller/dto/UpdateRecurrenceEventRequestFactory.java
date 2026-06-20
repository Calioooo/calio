package com.calio.calendar.controller.dto;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.entity.RecurrenceFrequency;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class UpdateRecurrenceEventRequestFactory {

    public UpdateRecurrenceEventRequest create(JsonNode requestBody) {
        return new UpdateRecurrenceEventRequest(
                nullableValue(requestBody, "title"),
                nullableValue(requestBody, "description"),
                parseInstant(requestBody, "startAt", ErrorCode.RECURRENCE_UPDATE_START_AT_INVALID),
                parseInstant(requestBody, "endAt", ErrorCode.RECURRENCE_UPDATE_END_AT_INVALID),
                RecurrenceFrequency.valueOf(requestBody.get("recurrenceFrequency").asString())
        );
    }

    private Instant parseInstant(JsonNode requestBody, String fieldName, ErrorCode invalidErrorCode) {
        try {
            return Instant.parse(requestBody.get(fieldName).asString());
        } catch (DateTimeParseException exception) {
            throw new CalioException(invalidErrorCode);
        }
    }

    private NullableUpdateValue<String> nullableValue(JsonNode requestBody, String fieldName) {
        if (!requestBody.has(fieldName)) {
            return NullableUpdateValue.omitted();
        }

        JsonNode value = requestBody.get(fieldName);
        if (value.isNull()) {
            return NullableUpdateValue.present(null);
        }

        return NullableUpdateValue.present(value.asString());
    }
}
