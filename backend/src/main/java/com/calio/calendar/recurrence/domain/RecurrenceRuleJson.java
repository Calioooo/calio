package com.calio.calendar.recurrence.domain;

import java.util.ArrayList;
import java.util.List;

final class RecurrenceRuleJson {

    private RecurrenceRuleJson() {
    }

    static String encode(List<String> rules) {
        return rules.stream()
                .map(RecurrenceRuleJson::quote)
                .reduce((left, right) -> left + "," + right)
                .map(values -> "[" + values + "]")
                .orElse("[]");
    }

    static List<String> decode(String json) {
        if (json == null || json.length() < 2 || json.charAt(0) != '[' || json.charAt(json.length() - 1) != ']') {
            throw new IllegalStateException("Invalid recurrence_rule JSON.");
        }
        List<String> values = new ArrayList<>();
        int index = 1;
        while (index < json.length() - 1) {
            index = skipSeparatorWhitespace(json, index);
            if (index >= json.length() - 1) {
                break;
            }
            ParsedString parsed = parseString(json, index);
            values.add(parsed.value());
            index = parsed.nextIndex();
        }
        return List.copyOf(values);
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(character);
            }
        }
        return escaped.append('"').toString();
    }

    private static int skipSeparatorWhitespace(String json, int index) {
        int current = index;
        while (current < json.length() - 1) {
            char character = json.charAt(current);
            if (character == ',' || Character.isWhitespace(character)) {
                current++;
                continue;
            }
            break;
        }
        return current;
    }

    private static ParsedString parseString(String json, int index) {
        if (json.charAt(index) != '"') {
            throw new IllegalStateException("Invalid recurrence_rule JSON.");
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int current = index + 1; current < json.length() - 1; current++) {
            char character = json.charAt(current);
            if (escaped) {
                value.append(unescape(character));
                escaped = false;
                continue;
            }
            if (character == '\\') {
                escaped = true;
                continue;
            }
            if (character == '"') {
                return new ParsedString(value.toString(), current + 1);
            }
            value.append(character);
        }
        throw new IllegalStateException("Invalid recurrence_rule JSON.");
    }

    private static char unescape(char character) {
        return switch (character) {
            case '"' -> '"';
            case '\\' -> '\\';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> throw new IllegalStateException("Invalid recurrence_rule JSON escape.");
        };
    }

    private record ParsedString(String value, int nextIndex) {
    }
}
