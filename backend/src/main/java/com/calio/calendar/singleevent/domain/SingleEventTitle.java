package com.calio.calendar.singleevent.domain;

import com.calio.calendar.common.domain.CalendarEventTitle;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record SingleEventTitle(
    @Column(name = "title", nullable = false, length = SingleEventTitle.MAX_LENGTH) String value) {

  public static final int MAX_LENGTH = CalendarEventTitle.MAX_LENGTH;

  public SingleEventTitle {
    CalendarEventTitle.requireValid(value);
  }
}
