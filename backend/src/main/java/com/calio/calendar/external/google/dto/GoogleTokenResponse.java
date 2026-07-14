package com.calio.calendar.external.google.dto;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public record GoogleTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public static GoogleTokenResponse fromJson(String json, ObjectMapper objectMapper) throws JacksonException {
        if (json == null || json.isBlank()) {
            throw new CalioException(ErrorCode.GOOGLE_TOKEN_RESPONSE_INVALID);
        }

        JsonNode root = objectMapper.readTree(json);
        String accessToken = textOrNull(root.get("access_token"));
        String refreshToken = textOrNull(root.get("refresh_token"));
        long expiresIn = longOrZero(root.get("expires_in"));
        String tokenType = textOrNull(root.get("token_type"));

        if (isBlank(accessToken) || isBlank(refreshToken) || expiresIn <= 0) {
            throw new CalioException(ErrorCode.GOOGLE_TOKEN_RESPONSE_INVALID);
        }

        if (tokenType != null && !BEARER_TOKEN_TYPE.equals(tokenType)) {
            throw new CalioException(ErrorCode.GOOGLE_TOKEN_RESPONSE_INVALID);
        }

        return new GoogleTokenResponse(accessToken, refreshToken, expiresIn);
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        return node.asString();
    }

    private static long longOrZero(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }

        return node.asLong();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
