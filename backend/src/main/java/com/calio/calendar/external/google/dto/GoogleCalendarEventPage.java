package com.calio.calendar.external.google.dto;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record GoogleCalendarEventPage(
        List<GoogleCalendarEventItem> items,
        String nextPageToken,
        String nextSyncToken,
        String timeZone
) {

    public static GoogleCalendarEventPage fromJson(
            String json,
            ObjectMapper objectMapper
    ) throws JacksonException {
        if (json == null || json.isBlank()) {
            throw invalidResponse();
        }
        JsonNode root = objectMapper.readTree(json);
        List<GoogleCalendarEventItem> items = parseItems(root.get("items"));
        String nextPageToken = textOrNull(root.get("nextPageToken"));
        String nextSyncToken = textOrNull(root.get("nextSyncToken"));
        if (hasText(nextPageToken) && hasText(nextSyncToken)) {
            throw invalidResponse();
        }
        return new GoogleCalendarEventPage(
                List.copyOf(items),
                nextPageToken,
                nextSyncToken,
                textOrNull(root.get("timeZone"))
        );
    }

    public boolean hasNextPage() {
        return hasText(nextPageToken);
    }

    private static List<GoogleCalendarEventItem> parseItems(JsonNode itemsNode) {
        if (itemsNode == null || itemsNode.isNull()) {
            return List.of();
        }
        if (!itemsNode.isArray()) {
            throw invalidResponse();
        }
        List<GoogleCalendarEventItem> items = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            items.add(parseItem(itemNode));
        }
        return items;
    }

    private static GoogleCalendarEventItem parseItem(JsonNode itemNode) {
        String id = textOrNull(itemNode.get("id"));
        String status = textOrNull(itemNode.get("status"));
        if (!hasText(id)) {
            throw invalidResponse();
        }
        GoogleCalendarEventTime start = parseTime(itemNode.get("start"));
        GoogleCalendarEventTime end = parseTime(itemNode.get("end"));
        if (!"cancelled".equals(status) && !validSchedulePair(start, end)) {
            throw invalidResponse();
        }
        return new GoogleCalendarEventItem(
                id,
                status,
                textOrNull(itemNode.get("etag")),
                GoogleCalendarEventItem.parseUpdatedAt(textOrNull(itemNode.get("updated"))),
                textOrNull(itemNode.get("summary")),
                textOrNull(itemNode.get("description")),
                stringList(itemNode.get("recurrence")),
                textOrNull(itemNode.get("recurringEventId")),
                start,
                end
        );
    }

    private static GoogleCalendarEventTime parseTime(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return new GoogleCalendarEventTime(
                textOrNull(node.get("date")),
                textOrNull(node.get("dateTime")),
                textOrNull(node.get("timeZone"))
        );
    }

    private static boolean validSchedulePair(
            GoogleCalendarEventTime start,
            GoogleCalendarEventTime end
    ) {
        if (start == null || end == null) {
            return false;
        }
        return (start.isAllDay() && end.isAllDay()) || (start.isTimed() && end.isTimed());
    }

    private static List<String> stringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw invalidResponse();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            String text = textOrNull(value);
            if (hasText(text)) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static CalioException invalidResponse() {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID);
    }
}
