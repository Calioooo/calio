package com.calio.calendar.groupcalendar.event.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Embeddable;

@Embeddable
public record GroupCalendarEventTitle(String value) {

  public static final int MAX_LENGTH = 80;

  public GroupCalendarEventTitle {
    if (value == null || value.codePointCount(0, value.length()) > MAX_LENGTH) {
      throw new CalioException(ErrorCode.INVALID_EVENT_TITLE);
    }
  }
}
