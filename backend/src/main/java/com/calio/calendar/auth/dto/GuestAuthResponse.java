package com.calio.calendar.auth.dto;

public record GuestAuthResponse(String accessToken, String tokenType) {

    private static final String BEARER_TOKEN_TYPE = "Bearer";

    public static GuestAuthResponse bearer(String accessToken) {
        return new GuestAuthResponse(accessToken, BEARER_TOKEN_TYPE);
    }
}
