package com.calio.calendar.external.google.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public GoogleTokenResponse {
        if (accessToken == null
                || accessToken.isBlank()
                || refreshToken == null
                || refreshToken.isBlank()
                || expiresIn <= 0) {
            throw new CalioException(ErrorCode.GOOGLE_TOKEN_RESPONSE_INVALID);
        }
    }

    @JsonCreator
    public GoogleTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn,
            @JsonProperty("token_type") String tokenType
    ) {
        this(accessToken, refreshToken, expiresIn);
        if (tokenType != null && !BEARER_TOKEN_TYPE.equals(tokenType)) {
            throw new CalioException(ErrorCode.GOOGLE_TOKEN_RESPONSE_INVALID);
        }
    }
}
