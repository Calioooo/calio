package com.calio.calendar.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskTest {

    @Test
    @DisplayName("새 Task는 미완료 상태이며 완료 시각을 갖지 않는다")
    void givenNewTask_whenReadCompletionState_thenIsUncompletedWithoutCompletedAt() {
        // when
        Task task = task();

        // then
        assertThat(task.isCompleted()).isFalse();
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("미완료 Task를 완료 상태로 변경하면 완료 시각을 함께 기록한다")
    void givenUncompletedTask_whenChangeCompleted_thenStoresCompletedAt() {
        // given
        Task task = task();
        Instant completedAt = Instant.parse("2026-08-09T01:00:00Z");

        // when
        task.changeCompleted(completedAt);

        // then
        assertThat(task.isCompleted()).isTrue();
        assertThat(task.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    @DisplayName("이미 완료된 Task를 다시 완료 상태로 변경해도 최초 완료 시각을 보존한다")
    void givenCompletedTask_whenChangeCompletedAgain_thenPreservesOriginalCompletedAt() {
        // given
        Task task = task();
        Instant originalCompletedAt = Instant.parse("2026-08-09T01:00:00Z");
        task.changeCompleted(originalCompletedAt);

        // when
        task.changeCompleted(Instant.parse("2026-08-09T02:00:00Z"));

        // then
        assertThat(task.isCompleted()).isTrue();
        assertThat(task.getCompletedAt()).isEqualTo(originalCompletedAt);
    }

    @Test
    @DisplayName("완료된 Task를 미완료 상태로 변경하면 완료 시각을 제거한다")
    void givenCompletedTask_whenChangeUncompleted_thenClearsCompletedAt() {
        // given
        Task task = task();
        task.changeCompleted(Instant.parse("2026-08-09T01:00:00Z"));

        // when
        task.changeUncompleted();

        // then
        assertThat(task.isCompleted()).isFalse();
        assertThat(task.getCompletedAt()).isNull();
    }

    private Task task() {
        return new Task("할 일", new Account());
    }
}
