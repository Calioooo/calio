package com.calio.calendar.integration.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class GoogleContentHash {

    private static final Pattern FORMAT = Pattern.compile("v1:[0-9a-f]{64}");

    private GoogleContentHash() {
    }

    public static String digest(String type, Object... fields) {
        MessageDigest digest = sha256();
        append(digest, type);
        for (Object field : fields) {
            append(digest, field == null ? null : field.toString());
        }
        return "v1:" + HexFormat.of().formatHex(digest.digest());
    }

    public static String requireValid(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Content hash must match v1:<64 lowercase hexadecimal SHA-256>"
            );
        }
        return value;
    }

    private static void append(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
