package com.calio.calendar.groupspace.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.text.BreakIterator;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GroupSpaceInputNormalizer {

    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[A-Za-z0-9가-힣]{1,9}$");
    private static final int MAX_NAME_CODE_POINTS = 30;
    private static final int MAX_EMOJI_CODE_POINTS = 32;

    public String normalizeName(String value) {
        String normalized = stripWhitespace(normalizeRequired(value));
        if (normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > MAX_NAME_CODE_POINTS
                || containsControlCharacter(normalized)) {
            throw validationFailed();
        }
        return normalized;
    }

    public String normalizeNickname(String value) {
        String normalized = normalizeRequired(value);
        if (!NICKNAME_PATTERN.matcher(normalized).matches()) {
            throw validationFailed();
        }
        return normalized;
    }

    public String normalizeEmoji(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (isWhitespaceOnly(value)) {
            throw validationFailed();
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if (normalized.codePointCount(0, normalized.length()) > MAX_EMOJI_CODE_POINTS
                || containsControlCharacter(normalized)
                || graphemeCount(normalized) != 1
                || !containsEmojiProperty(normalized)) {
            throw validationFailed();
        }
        return normalized;
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isEmpty() || isWhitespaceOnly(value)) {
            throw validationFailed();
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    private String stripWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private boolean isWhitespaceOnly(String value) {
        return value.codePoints().allMatch(this::isWhitespace);
    }

    private boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private int graphemeCount(String value) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(value);
        int count = 0;
        for (int boundary = iterator.first(); boundary != BreakIterator.DONE; boundary = iterator.next()) {
            if (boundary != 0) {
                count++;
            }
        }
        return count;
    }

    private boolean containsEmojiProperty(String value) {
        return value.codePoints().anyMatch(codePoint ->
                UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI)
                        || UCharacter.hasBinaryProperty(codePoint, UProperty.EXTENDED_PICTOGRAPHIC)
        );
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.getType(codePoint) == Character.CONTROL
        );
    }

    private CalioException validationFailed() {
        return new CalioException(ErrorCode.VALIDATION_FAILED);
    }
}
