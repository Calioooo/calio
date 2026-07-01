package com.calio.calendar.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TreeNode;
import tools.jackson.databind.ObjectMapper;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HolidayApiItem(
        String locdate,
        String dateName,
        String isHoliday
) {

    static HolidayApiItem from(TreeNode item, ObjectMapper objectMapper) throws JacksonException {
        return objectMapper.treeToValue(item, HolidayApiItem.class);
    }
}
