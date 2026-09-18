package com.calio.calendar.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskTitleTest {

  @Test
  @DisplayName("Task 제목은 공백이 아니고 40자 이하여야 한다")
  void givenValidTitle_whenCreate_thenKeepsValue() {
    TaskTitle title = new TaskTitle("할 일");

    assertThat(title.value()).isEqualTo("할 일");
  }

  @Test
  @DisplayName("공백이거나 40자를 초과한 Task 제목은 생성할 수 없다")
  void givenInvalidTitle_whenCreate_thenRejectsValue() {
    assertThatThrownBy(() -> new TaskTitle(" "))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TASK_TITLE));
    assertThatThrownBy(() -> new TaskTitle("a".repeat(TaskTitle.MAX_LENGTH + 1)))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TASK_TITLE));
  }
}
