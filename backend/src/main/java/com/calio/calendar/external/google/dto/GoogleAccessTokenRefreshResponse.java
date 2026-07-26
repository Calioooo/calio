package com.calio.calendar.external.google.dto;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleAccessTokenRefreshResponse(
        String accessToken,
        long expiresIn
) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public GoogleAccessTokenRefreshResponse {
        if (accessToken == null || accessToken.isBlank() || expiresIn <= 0) {
            throw invalidResponse();
        }
    }

    @JsonCreator
    public GoogleAccessTokenRefreshResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("token_type") String tokenType
    ) {
        this(accessToken, expiresIn);
        if (!BEARER_TOKEN_TYPE.equals(tokenType)) {
            throw invalidResponse();
        }
    }

    private static CalioException invalidResponse() {
        return new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED);
    }
}
