package com.calio.calendar.groupspace.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Embeddable;

@Embeddable
public record GroupSpaceEmoji(String value) {

  private static final int MAX_CODE_POINTS = 64;

  public GroupSpaceEmoji {
    if (value == null || value.isEmpty()) {
      throw validationFailed();
    }
    if (value.codePointCount(0, value.length()) > MAX_CODE_POINTS) {
      throw validationFailed();
    }
  }

  public static GroupSpaceEmoji fromNullable(String value) {
    return value == null || value.isEmpty() ? null : new GroupSpaceEmoji(value);
  }

  private static CalioException validationFailed() {
    return new CalioException(ErrorCode.VALIDATION_FAILED);
  }
}
