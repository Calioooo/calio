package com.calio.calendar.auth.dto;

public record AnonymousAuthResponse(String accessToken, String tokenType) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public static AnonymousAuthResponse bearer(String accessToken) {
        return new AnonymousAuthResponse(accessToken, BEARER_TOKEN_TYPE);
    }
}
