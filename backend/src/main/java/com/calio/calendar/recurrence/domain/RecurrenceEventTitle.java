package com.calio.calendar.recurrence.domain;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import jakarta.persistence.Embeddable;

@Embeddable
public record RecurrenceEventTitle(String value) {

  public static final int MAX_LENGTH = 255;

  public RecurrenceEventTitle {
    if (value == null || value.length() > MAX_LENGTH) {
      throw new CalioException(ErrorCode.INVALID_EVENT_TITLE);
    }
  }
}
