package com.calio.calendar.controller.dto;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.entity.RecurrenceFrequency;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class UpdateRecurrenceEventRequestFactory {

    private static final String RECURRENCE_EVENT_SCOPE = "RECURRENCE_EVENT";
    private static final String SINGLE_OCCURRENCE_SCOPE = "SINGLE_OCCURRENCE";

    private final UpdateRecurrenceEventValidator validator;
    private final JsonParser jsonParser;

    public UpdateRecurrenceEventRequestFactory(
            UpdateRecurrenceEventValidator validator,
            JsonParser jsonParser
    ) {
        this.validator = validator;
        this.jsonParser = jsonParser;
    }

    public UpdateRecurrenceEventCommand create(JsonNode body, String updateScope) {
        validator.validate(body, updateScope);

        UpdateRecurrenceEventRequest request = new UpdateRecurrenceEventRequest(
                jsonParser.parseText(body, "recurrenceTitle"),
                jsonParser.parseText(body, "recurrenceDescription"),
                jsonParser.parseLocalDate(body, "recurrenceStartDate"),
                jsonParser.parseLocalDate(body, "recurrenceEndDate"),
                jsonParser.parseLocalTime(body, "recurrenceStartTime"),
                jsonParser.parseLocalTime(body, "recurrenceEndTime"),
                parseRecurrenceFrequency(body),
                jsonParser.parseInstant(body, "targetOccurrenceStartAt"),
                jsonParser.parseInstant(body, "modifiedStartAt"),
                jsonParser.parseInstant(body, "modifiedEndAt")
        );

        return new UpdateRecurrenceEventCommand(
                toUpdateType(updateScope),
                request,
                Set.copyOf(body.propertyNames())
        );
    }

    private UpdateType toUpdateType(String updateScope) {
        if (RECURRENCE_EVENT_SCOPE.equals(updateScope)) {
            return UpdateType.WHOLE;
        }

        if (SINGLE_OCCURRENCE_SCOPE.equals(updateScope)) {
            return UpdateType.OCCURRENCE;
        }

        throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }

    private RecurrenceFrequency parseRecurrenceFrequency(JsonNode body) {
        String frequency = jsonParser.parseText(body, "recurrenceFrequency");
        if (frequency == null) {
            return null;
        }

        try {
            return RecurrenceFrequency.valueOf(frequency);
        } catch (IllegalArgumentException exception) {
            throw new CalioException(ErrorCode.INVALID_RECURRENCE_FREQUENCY);
        }
    }
}
