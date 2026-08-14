package com.calio.calendar.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.task.controller.dto.CreateTaskRequest;
import com.calio.calendar.task.controller.dto.TaskResponse;
import com.calio.calendar.task.controller.dto.UpdateTaskTitleRequest;
import com.calio.calendar.task.domain.Task;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-09T01:02:03.123456789Z");

    @Mock
    private TaskQueryService taskQueryService;

    @Mock
    private TaskCommandService taskCommandService;

    @Mock
    private AccountQueryService accountQueryService;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(
                taskQueryService,
                taskCommandService,
                accountQueryService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("Task 생성은 account를 연결한 domain을 Command에 전달하고 저장 결과를 반환한다")
    void givenCreateRequest_whenCreateTask_thenDelegatesCanonicalTaskToCommand() {
        // given
        Account account = new Account();
        when(accountQueryService.getAccount(1L)).thenReturn(account);
        when(taskCommandService.createTask(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            ReflectionTestUtils.setField(task, "taskId", 10L);
            return task;
        });

        // when
        TaskResponse response = taskService.createTask(1L, new CreateTaskRequest("새 할 일"));

        // then
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskCommandService).createTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getTaskTitle()).isEqualTo("새 할 일");
        assertThat(taskCaptor.getValue().getAccount()).isSameAs(account);
        assertThat(response.taskId()).isEqualTo(10L);
        assertThat(response.taskTitle()).isEqualTo("새 할 일");
    }

    @Test
    @DisplayName("Task 목록 조회는 미완료 첫 20개 조건을 Query에 전달하고 domain 결과를 DTO로 변환한다")
    void givenAccountId_whenListTasks_thenQueriesCanonicalPageAndMapsResponses() {
        // given
        List<Task> tasks = List.of(task("할 일"));
        when(taskQueryService.listTasks(eq(1L), any(Pageable.class))).thenReturn(tasks);

        // when
        List<TaskResponse> responses = taskService.listTasks(1L);

        // then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(taskQueryService).listTasks(eq(1L), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("taskId"))
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.ASC);
        assertThat(responses)
                .extracting(TaskResponse::taskId, TaskResponse::taskTitle)
                .containsExactly(tuple(10L, "할 일"));
    }

    @Test
    @DisplayName("Task 완료는 소유 Task를 조회한 뒤 Clock의 microsecond 시각으로 Command를 실행한다")
    void givenOwnedTask_whenCompleteTask_thenDelegatesWithCanonicalPersistenceTime() {
        // given
        Task task = task("할 일");
        when(taskQueryService.getTask(1L, 10L)).thenReturn(task);
        doAnswer(invocation -> {
            Task target = invocation.getArgument(0);
            target.changeCompleted(invocation.getArgument(1));
            return null;
        }).when(taskCommandService).changeTaskCompleted(any(Task.class), any(Instant.class));

        // when
        TaskResponse response = taskService.completeTask(1L, 10L);

        // then
        Instant expectedCompletedAt = Instant.parse("2026-08-09T01:02:03.123456Z");
        verify(taskCommandService).changeTaskCompleted(task, expectedCompletedAt);
        assertThat(response.isCompleted()).isTrue();
        assertThat(response.completedAt()).isEqualTo(expectedCompletedAt);
    }

    @Test
    @DisplayName("Task 미완료 전환은 소유 Task를 조회한 뒤 Command에 위임한다")
    void givenCompletedTask_whenUncompleteTask_thenDelegatesToCommand() {
        // given
        Task task = task("할 일");
        task.changeCompleted(Instant.parse("2026-08-01T00:00:00Z"));
        when(taskQueryService.getTask(1L, 10L)).thenReturn(task);
        doAnswer(invocation -> {
            Task target = invocation.getArgument(0);
            target.changeUncompleted();
            return null;
        }).when(taskCommandService).changeTaskUncompleted(task);

        // when
        TaskResponse response = taskService.uncompleteTask(1L, 10L);

        // then
        verify(taskCommandService).changeTaskUncompleted(task);
        assertThat(response.isCompleted()).isFalse();
        assertThat(response.completedAt()).isNull();
    }

    @Test
    @DisplayName("완료된 Task의 제목 변경은 Command 실행 전에 거절한다")
    void givenCompletedTask_whenUpdateTitle_thenRejectsBeforeCommand() {
        // given
        Task task = task("기존 제목");
        task.changeCompleted(Instant.parse("2026-08-01T00:00:00Z"));
        when(taskQueryService.getTask(1L, 10L)).thenReturn(task);

        // when, then
        assertThatThrownBy(() -> taskService.updateTaskTitle(
                1L,
                10L,
                new UpdateTaskTitleRequest("변경 제목")
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.COMPLETED_TASK_TITLE_UPDATE_NOT_ALLOWED)
        );
        verify(taskCommandService, never()).updateTaskTitle(any(), any());
    }

    @Test
    @DisplayName("미완료 Task의 제목 변경은 소유 Task를 조회한 뒤 Command에 위임한다")
    void givenUncompletedTask_whenUpdateTitle_thenDelegatesToCommand() {
        // given
        Task task = task("기존 제목");
        when(taskQueryService.getTask(1L, 10L)).thenReturn(task);
        doAnswer(invocation -> {
            Task target = invocation.getArgument(0);
            target.updateTitle(invocation.getArgument(1));
            return null;
        }).when(taskCommandService).updateTaskTitle(task, "변경 제목");

        // when
        TaskResponse response = taskService.updateTaskTitle(
                1L,
                10L,
                new UpdateTaskTitleRequest("변경 제목")
        );

        // then
        verify(taskCommandService).updateTaskTitle(task, "변경 제목");
        assertThat(response.taskTitle()).isEqualTo("변경 제목");
    }

    @Test
    @DisplayName("완료 Task cleanup은 cutoff를 Command에 그대로 위임한다")
    void givenCutoff_whenDeleteCompletedTasksBefore_thenDelegatesToCommand() {
        // given
        Instant cutoff = Instant.parse("2026-07-10T00:00:00Z");
        when(taskCommandService.deleteCompletedTasksBefore(cutoff)).thenReturn(3);

        // when
        int deletedCount = taskService.deleteCompletedTasksBefore(cutoff);

        // then
        assertThat(deletedCount).isEqualTo(3);
        verify(taskCommandService).deleteCompletedTasksBefore(cutoff);
        verifyNoInteractions(taskQueryService);
    }

    private Task task(String title) {
        Task task = new Task(title, new Account());
        ReflectionTestUtils.setField(task, "taskId", 10L);
        return task;
    }
}
