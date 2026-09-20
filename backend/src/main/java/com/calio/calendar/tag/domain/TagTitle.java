package com.calio.calendar.tag.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Embeddable;
import java.util.Objects;

@Embeddable
public class TagTitle {

    public static final int MAX_TAG_TITLE_LENGTH = 20;

    private String value;

    protected TagTitle() {
    }

    public TagTitle(String value) {
        validate(value);
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void validate() {
        validate(value);
    }

    private void validate(String value) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > MAX_TAG_TITLE_LENGTH) {
            throw new CalioException(ErrorCode.VALIDATION_FAILED);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TagTitle that)) {
            return false;
        }
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
