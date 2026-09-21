package com.calio.calendar.account.domain;

public enum TimedReminderOffset {
  NONE(null),
  AT_START(0),
  MINUTES_5(5),
  MINUTES_10(10),
  MINUTES_30(30),
  MINUTES_60(60),
  MINUTES_120(120),
  MINUTES_1440(1440);

  private final Integer minutes;

  TimedReminderOffset(Integer minutes) {
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
