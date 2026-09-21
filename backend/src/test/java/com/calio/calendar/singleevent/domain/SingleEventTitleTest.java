package com.calio.calendar.singleevent.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SingleEventTitleTest {

  @Test
  @DisplayName("일정 제목은 공백을 포함할 수 있다")
  void givenBlankTitle_whenCreate_thenKeepsValue() {
    assertThat(new SingleEventTitle(" ").value()).isEqualTo(" ");
  }

  @Test
  @DisplayName("일정 제목은 입력값을 그대로 보존한다")
  void givenTitle_whenCreate_thenKeepsValue() {
    assertThat(new SingleEventTitle("팀 회의").value()).isEqualTo("팀 회의");
  }

  @Test
  @DisplayName("일정 제목은 80자를 초과할 수 없다")
  void givenOverlengthTitle_whenCreate_thenRejectsTitle() {
    assertThatThrownBy(() -> new SingleEventTitle("a".repeat(SingleEventTitle.MAX_LENGTH + 1)))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_EVENT_TITLE);
  }
}
