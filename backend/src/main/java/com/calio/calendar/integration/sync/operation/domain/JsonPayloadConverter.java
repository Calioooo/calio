package com.calio.calendar.integration.sync.operation.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class JsonPayloadConverter implements AttributeConverter<String, JsonNode> {

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();

    @Override
    public JsonNode convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(attribute);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Google operation target payload must be JSON", exception);
        }
    }

    @Override
    public String convertToEntityAttribute(JsonNode databaseValue) {
        if (databaseValue == null) {
            return null;
        }
        return databaseValue.isTextual() ? databaseValue.asText() : databaseValue.toString();
    }
}
