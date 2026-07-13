package com.calio.calendar.holiday.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HolidayApiItem(
        String locdate,
        String dateName,
        String isHoliday
) {

    static HolidayApiItem from(JsonNode item, ObjectMapper objectMapper) throws JacksonException {
        return objectMapper.treeToValue(item, HolidayApiItem.class);
    }
}
