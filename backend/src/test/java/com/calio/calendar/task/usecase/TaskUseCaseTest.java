package com.calio.calendar.task.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.task.controller.dto.TaskResponse;
import com.calio.calendar.task.domain.Task;
import com.calio.calendar.task.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskUseCaseTest {

  @Mock private AccountRepository accountRepository;

  @Mock private TaskRepository taskRepository;

  @Test
  @DisplayName("Task 생성 UseCase는 계정과 제목으로 Task aggregate를 생성해 저장한다")
  void givenAccountAndTitle_whenCreate_thenPersistsTaskAggregate() {
    // given
    Account account = new Account();
    when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
    when(taskRepository.save(org.mockito.ArgumentMatchers.any(Task.class)))
        .thenAnswer(
            invocation -> {
              Task task = invocation.getArgument(0);
              ReflectionTestUtils.setField(task, "taskId", 10L);
              return task;
            });
    CreateTaskUseCase useCase = new CreateTaskUseCase(accountRepository, taskRepository);

    // when
    TaskResponse response = useCase.create(1L, "새 할 일");

    // then
    ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
    verify(taskRepository).save(taskCaptor.capture());
    assertThat(taskCaptor.getValue().getAccountId()).isEqualTo(1L);
    assertThat(taskCaptor.getValue().getTaskTitle()).isEqualTo("새 할 일");
    assertThat(response.taskId()).isEqualTo(10L);
  }

  @Test
  @DisplayName("없는 계정으로 Task를 생성하면 ACCOUNT_NOT_FOUND를 반환한다")
  void givenMissingAccount_whenCreate_thenThrowsAccountNotFound() {
    // given
    when(accountRepository.findById(1L)).thenReturn(Optional.empty());
    CreateTaskUseCase useCase = new CreateTaskUseCase(accountRepository, taskRepository);

    // when, then
    assertThatThrownBy(() -> useCase.create(1L, "새 할 일"))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
  }

  @Test
  @DisplayName("Task 완료 UseCase는 소유한 Task만 완료 시각과 함께 변경한다")
  void givenOwnedTask_whenComplete_thenChangesAggregateWithClockTime() {
    // given
    Task task = task("할 일");
    when(taskRepository.findByTaskIdAndAccountId(10L, 1L)).thenReturn(Optional.of(task));
    CompleteTaskUseCase useCase =
        new CompleteTaskUseCase(
            taskRepository,
            Clock.fixed(Instant.parse("2026-08-09T01:02:03.123456789Z"), ZoneOffset.UTC));

    // when
    TaskResponse response = useCase.complete(1L, 10L);

    // then
    assertThat(response.isCompleted()).isTrue();
    assertThat(response.completedAt()).isEqualTo(Instant.parse("2026-08-09T01:02:03.123456Z"));
  }

  @Test
  @DisplayName("Task 제목 변경 UseCase는 소유하지 않은 Task에 TASK_NOT_FOUND를 반환한다")
  void givenMissingOwnedTask_whenUpdateTitle_thenThrowsTaskNotFound() {
    // given
    when(taskRepository.findByTaskIdAndAccountId(10L, 1L)).thenReturn(Optional.empty());
    UpdateTaskTitleUseCase useCase = new UpdateTaskTitleUseCase(taskRepository);

    // when, then
    assertThatThrownBy(() -> useCase.update(1L, 10L, "변경 제목"))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND));
  }

  private Task task(String title) {
    Task task = new Task(title, 1L);
    ReflectionTestUtils.setField(task, "taskId", 10L);
    return task;
  }
}
