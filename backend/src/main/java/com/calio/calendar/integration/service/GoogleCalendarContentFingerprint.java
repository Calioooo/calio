package com.calio.calendar.integration.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GoogleCalendarContentFingerprint {

    public static final String VERSION = "calio-owned-v1";

    public Fingerprint generalEvent(
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone
    ) {
        return fingerprint("GENERAL_EVENT", List.of(
                value(title), value(description), value(startAt), value(endAt),
                value(allDay), value(normalizeTimeZone(allDay, timeZone))
        ));
    }

    public Fingerprint recurrenceMaster(
            String title,
            String description,
            Instant firstStartAt,
            Instant firstEndAt,
            boolean allDay,
            String timeZone,
            List<String> recurrenceRules
    ) {
        List<String> fields = new ArrayList<>(List.of(
                value(title), value(description), value(firstStartAt), value(firstEndAt),
                value(allDay), value(normalizeTimeZone(allDay, timeZone))
        ));
        recurrenceRules.stream().sorted().map(this::value).forEach(fields::add);
        return fingerprint("RECURRENCE_MASTER", fields);
    }

    public Fingerprint activeOverride(
            String parentExternalIdentity,
            Instant originStartAt,
            String title,
            String description,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String timeZone
    ) {
        return fingerprint("RECURRENCE_OVERRIDE", List.of(
                value(parentExternalIdentity), value(originStartAt), value(false), value(title),
                value(description), value(startAt), value(endAt), value(allDay),
                value(normalizeTimeZone(allDay, timeZone))
        ));
    }

    public Fingerprint deletedOverride(String parentExternalIdentity, Instant originStartAt) {
        return fingerprint("RECURRENCE_OVERRIDE", List.of(
                value(parentExternalIdentity), value(originStartAt), value(true)
        ));
    }

    private Fingerprint fingerprint(String type, List<String> fields) {
        String projection = VERSION + '|' + type + '|' + String.join("|", fields);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(projection.getBytes(StandardCharsets.UTF_8));
            return new Fingerprint(VERSION, HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String value(Object value) {
        if (value == null) {
            return "-";
        }
        String text = value instanceof Instant instant ? instant.toString() : value.toString();
        return text.length() + ":" + text;
    }

    private String normalizeTimeZone(boolean allDay, String timeZone) {
        return allDay ? null : timeZone;
    }

    public record Fingerprint(String version, String hash) {
    }
}
