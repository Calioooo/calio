package com.calio.calendar.recurrence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurrenceEventTitleTest {

  @Test
  @DisplayName("외부 반복 일정 제목을 저장하기 위해 255자까지 허용한다")
  void givenExternalTitleWithinStorageLimit_whenCreate_thenKeepsValue() {
    String title = "😀".repeat(RecurrenceEventTitle.MAX_LENGTH);

    assertThat(new RecurrenceEventTitle(title).value()).isEqualTo(title);
  }

  @Test
  @DisplayName("반복 일정 제목은 저장 한도인 255자를 초과할 수 없다")
  void givenOverlengthTitle_whenCreate_thenRejectsTitle() {
    assertThatThrownBy(
            () -> new RecurrenceEventTitle("😀".repeat(RecurrenceEventTitle.MAX_LENGTH + 1)))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_EVENT_TITLE);
  }
}
