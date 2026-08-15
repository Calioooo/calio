package com.calio.calendar.integration.mapping.domain;

public final class GoogleCalendarContentHashValidator {

    private static final String SHA_256_HEX_PATTERN = "^[0-9a-f]{64}$";

    private GoogleCalendarContentHashValidator() {
    }

    public static String requireValid(String value) {
        if (value == null || !value.matches(SHA_256_HEX_PATTERN)) {
            throw new IllegalArgumentException("Google Calendar content hash must be a SHA-256 digest");
        }
        return value;
    }
}
