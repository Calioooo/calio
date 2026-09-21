package com.calio.calendar.account.domain;

public enum ImportantReminderOffset {
  NONE(null),
  MINUTES_30(30),
  MINUTES_60(60),
  MINUTES_120(120),
  MINUTES_1440(1440);

  private final Integer minutes;

  ImportantReminderOffset(Integer minutes) {
    this.minutes = minutes;
  }

  public boolean isDisabled() {
    return this == NONE;
  }

  public int minutes() {
    if (this == NONE) {
      throw new IllegalStateException("Disabled reminder does not have an offset.");
    }
    return minutes;
  }
}
