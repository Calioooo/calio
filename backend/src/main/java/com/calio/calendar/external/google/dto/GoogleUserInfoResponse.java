package com.calio.calendar.external.google.dto;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record GoogleUserInfoResponse(
        String subject,
        String email
) {

    public static GoogleUserInfoResponse fromJson(String json, ObjectMapper objectMapper) throws JacksonException {
        if (json == null || json.isBlank()) {
            throw new CalioException(ErrorCode.GOOGLE_USER_INFO_INVALID);
        }

        JsonNode root = objectMapper.readTree(json);
        String subject = textOrNull(root.get("sub"));
        String email = textOrNull(root.get("email"));

        if (isBlank(subject) || isBlank(email) || isEmailExplicitlyUnverified(root.get("email_verified"))) {
            throw new CalioException(ErrorCode.GOOGLE_USER_INFO_INVALID);
        }

        return new GoogleUserInfoResponse(subject, email);
    }

    private static boolean isEmailExplicitlyUnverified(JsonNode node) {
        return node != null && !node.isNull() && !node.asBoolean();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        return node.asString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
