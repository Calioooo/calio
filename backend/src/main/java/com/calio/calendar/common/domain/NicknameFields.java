package com.calio.calendar.common.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.text.Normalizer;
import java.util.regex.Pattern;

public final class NicknameFields {

    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[A-Za-z0-9가-힣]{1,9}$");

    private NicknameFields() {
    }

    public static String normalize(String value) {
        if (value == null) {
            throw validationFailed();
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        if (!NICKNAME_PATTERN.matcher(normalized).matches()) {
            throw validationFailed();
        }
        return normalized;
    }

    private static CalioException validationFailed() {
        return new CalioException(ErrorCode.VALIDATION_FAILED);
    }
}
