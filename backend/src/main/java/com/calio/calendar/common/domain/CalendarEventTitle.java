package com.calio.calendar.common.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;

public final class CalendarEventTitle {

  public static final int MAX_LENGTH = 80;

  private CalendarEventTitle() {}

  public static String requireValid(String value) {
    if (value == null || value.length() > MAX_LENGTH) {
      throw new CalioException(ErrorCode.INVALID_EVENT_TITLE);
    }
    return value;
  }
}
