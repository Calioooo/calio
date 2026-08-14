package com.calio.calendar.holiday.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HolidayApiItem(
        @JsonProperty("locdate") String localDate,
        String dateName,
        String isHoliday
) {

    static HolidayApiItem from(JsonNode item, ObjectMapper objectMapper) throws JacksonException {
        return objectMapper.treeToValue(item, HolidayApiItem.class);
    }
}
