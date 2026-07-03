package com.calio.calendar.repository.entity;

import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import jakarta.persistence.Embeddable;
import java.util.Locale;
import java.util.regex.Pattern;

@Embeddable
public class ColorCode {

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private String value;

    protected ColorCode() {
    }

    public ColorCode(String value) {
        validate(value);
        this.value = value.toUpperCase(Locale.ROOT);
    }

    private void validate(String value) {
        if (value == null || !COLOR_CODE_PATTERN.matcher(value).matches()) {
            throw new CalioException(ErrorCode.INVALID_TAG_COLOR_CODE);
        }
    }

    public String getValue() {
        return value;
    }
}
