package com.calio.calendar.groupspace.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Embeddable;
import java.text.Normalizer;

@Embeddable
public record GroupSpaceName(String value) {

  private static final int MAX_CODE_POINTS = 30;

  public GroupSpaceName {
    value = normalize(value);
  }

  private static String normalize(String value) {
    if (value == null) {
      throw validationFailed();
    }
    String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
    if (normalized.isEmpty()
        || normalized.codePointCount(0, normalized.length()) > MAX_CODE_POINTS
        || normalized.codePoints().anyMatch(Character::isISOControl)) {
      throw validationFailed();
    }
    return normalized;
  }

  private static CalioException validationFailed() {
    return new CalioException(ErrorCode.VALIDATION_FAILED);
  }
}
