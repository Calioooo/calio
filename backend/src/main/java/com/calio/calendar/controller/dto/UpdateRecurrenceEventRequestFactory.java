package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.RecurrenceFrequency;
import java.time.Instant;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class UpdateRecurrenceEventRequestFactory {

    public UpdateRecurrenceEventRequest create(JsonNode requestBody) {
        return new UpdateRecurrenceEventRequest(
                nullableValue(requestBody, "title"),
                nullableValue(requestBody, "description"),
                Instant.parse(requestBody.get("startAt").asString()),
                Instant.parse(requestBody.get("endAt").asString()),
                RecurrenceFrequency.valueOf(requestBody.get("recurrenceFrequency").asString())
        );
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
