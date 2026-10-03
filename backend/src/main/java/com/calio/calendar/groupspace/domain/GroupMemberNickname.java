package com.calio.calendar.groupspace.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Embeddable;
import java.text.Normalizer;
import java.util.regex.Pattern;

@Embeddable
public record GroupMemberNickname(String value) {

  private static final Pattern VALID_PATTERN = Pattern.compile("^[A-Za-z0-9가-힣]{1,9}$");

  public GroupMemberNickname {
    value = normalize(value);
  }

  private static String normalize(String value) {
    if (value == null) {
      throw validationFailed();
    }
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
    if (!VALID_PATTERN.matcher(normalized).matches()) {
      throw validationFailed();
    }
    return normalized;
  }

  private static CalioException validationFailed() {
    return new CalioException(ErrorCode.VALIDATION_FAILED);
  }
}
