package com.calio.calendar.recurrence.domain;

import java.time.Instant;
import java.util.List;

/** 반복 일정 정의가 해당 원래 시작값의 회차를 생성하는지 판단한다. */
public interface RecurrenceOriginMatcher {

  boolean containsOrigin(
      RecurrenceSchedule schedule, List<String> recurrenceRules, Instant originStartAt);
}
