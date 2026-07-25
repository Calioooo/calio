package com.calio.calendar.external.google.dto;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record GoogleAccessTokenRefreshResponse(
        String accessToken,
        long expiresIn
) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public static GoogleAccessTokenRefreshResponse fromJson(
            String json,
            ObjectMapper objectMapper
    ) throws JacksonException {
        if (json == null || json.isBlank()) {
            throw invalidResponse();
        }

        JsonNode root = objectMapper.readTree(json);
        String accessToken = textOrNull(root.get("access_token"));
        long expiresIn = longOrZero(root.get("expires_in"));
        String tokenType = textOrNull(root.get("token_type"));
        if (isBlank(accessToken) || expiresIn <= 0) {
            throw invalidResponse();
        }
        if (!BEARER_TOKEN_TYPE.equals(tokenType)) {
            throw invalidResponse();
        }
        return new GoogleAccessTokenRefreshResponse(accessToken, expiresIn);
    }

    private static CalioException invalidResponse() {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED);
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    private static long longOrZero(JsonNode node) {
        return node == null || node.isNull() ? 0 : node.asLong();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
