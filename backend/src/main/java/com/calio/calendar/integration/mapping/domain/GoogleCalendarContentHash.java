package com.calio.calendar.integration.mapping.domain;

public record GoogleCalendarContentHash(String value) {

    private static final String SHA_256_HEX_PATTERN = "^[0-9a-f]{64}$";

    public GoogleCalendarContentHash {
        if (value == null || !value.matches(SHA_256_HEX_PATTERN)) {
            throw new IllegalArgumentException("Google Calendar content hash must be a SHA-256 digest");
        }
    }

    public static String requireValid(String value) {
        return new GoogleCalendarContentHash(value).value();
    }
}
