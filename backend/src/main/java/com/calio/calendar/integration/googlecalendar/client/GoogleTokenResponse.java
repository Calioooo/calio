package com.calio.calendar.integration.googlecalendar.client;

public record GoogleTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds
) {
}
