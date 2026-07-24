package com.calio.calendar.recurrence.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

@Converter
public class RecurrenceRuleJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectReader RECURRENCE_RULES_READER = OBJECT_MAPPER.readerForListOf(String.class);

    @Override
    public String convertToDatabaseColumn(List<String> recurrenceRules) {
        try {
            return OBJECT_MAPPER.writeValueAsString(recurrenceRules);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Failed to serialize recurrence rules.", exception);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String json) {
        try {
            return List.copyOf(RECURRENCE_RULES_READER.readValue(json));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid recurrence_rule JSON.", exception);
        }
    }
}
