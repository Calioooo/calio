package com.calio.calendar.task.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;

public record TaskTitle(String value) {

  public static final int MAX_LENGTH = 40;

  public TaskTitle {
    if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
      throw new CalioException(ErrorCode.INVALID_TASK_TITLE);
    }
  }
}
