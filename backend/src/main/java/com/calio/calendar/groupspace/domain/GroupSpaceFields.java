package com.calio.calendar.groupspace.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.text.Normalizer;
import java.util.regex.Pattern;

public final class GroupSpaceFields {

    private static final int MAX_NAME_CODE_POINTS = 30;
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[A-Za-z0-9가-힣]{1,9}$");

    private GroupSpaceFields() {
    }

    public static String normalizeName(String value) {
        if (value == null) {
            throw validationFailed();
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (isInvalidName(normalized)) {
            throw validationFailed();
        }
        return normalized;
    }

    public static String normalizeNickname(String value) {
        if (value == null) {
            throw validationFailed();
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if (!NICKNAME_PATTERN.matcher(normalized).matches()) {
            throw validationFailed();
        }
        return normalized;
    }

    private static boolean isInvalidName(String value) {
        int codePointCount = value.codePointCount(0, value.length());
        return codePointCount == 0
                || codePointCount > MAX_NAME_CODE_POINTS
                || value.codePoints().anyMatch(Character::isISOControl);
    }

    private static CalioException validationFailed() {
        return new CalioException(ErrorCode.VALIDATION_FAILED);
    }
}
