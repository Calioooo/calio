package com.calio.calendar.tag.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Embeddable;

@Embeddable
public record TagTitle(String value) {

  public static final int MAX_TAG_TITLE_LENGTH = 20;

  public TagTitle {
    validate(value);
  }

  private static void validate(String value) {
    if (value == null
        || value.isBlank()
        || value.codePointCount(0, value.length()) > MAX_TAG_TITLE_LENGTH) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
  }
}
