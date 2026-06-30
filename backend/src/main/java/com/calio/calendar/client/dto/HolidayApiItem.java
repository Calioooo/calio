package com.calio.calendar.client.dto;

import tools.jackson.databind.JsonNode;

public record HolidayApiItem(
        String locdate,
        String dateName,
        String isHoliday
) {

    static HolidayApiItem from(JsonNode node) {
        return new HolidayApiItem(
                textOrNull(node.get("locdate")),
                textOrNull(node.get("dateName")),
                textOrNull(node.get("isHoliday"))
        );
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        return node.asText();
    }
}
