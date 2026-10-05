package com.calio.calendar.recurrence.repository.dto;

import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;

/** 회차 변경 값과 응답에 필요한 소유 시리즈를 함께 조회한 결과. */
public record RecurrenceOverrideView(
    RecurrenceEvent recurrenceEvent, RecurrenceEventOverride override) {}
