package com.calio.calendar.client.dto;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record HolidayApiResponse(
        String resultCode,
        List<HolidayApiItem> items
) {

    public static HolidayApiResponse fromJson(String json, ObjectMapper objectMapper) throws JacksonException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode response = root.path("response");
        String resultCode = textOrNull(response.path("header").get("resultCode"));
        List<HolidayApiItem> items = itemsFrom(response.path("body").path("items").path("item"));

        return new HolidayApiResponse(resultCode, items);
    }

    public boolean isSuccess() {
        return "00".equals(resultCode);
    }

    private static List<HolidayApiItem> itemsFrom(JsonNode itemNode) {
        if (itemNode.isMissingNode() || itemNode.isNull()) {
            return List.of();
        }

        if (itemNode.isArray()) {
            List<HolidayApiItem> items = new ArrayList<>();
            for (JsonNode node : itemNode) {
                items.add(HolidayApiItem.from(node));
            }
            return items;
        }

        if (itemNode.isObject()) {
            return List.of(HolidayApiItem.from(itemNode));
        }

        return List.of();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        return node.asText();
    }
}
