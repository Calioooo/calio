package com.calio.calendar.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.task.domain.Task;
import com.calio.calendar.task.repository.TaskRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TaskCommandServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskCommandService taskCommandService;

    @Test
    @DisplayName("CommandService의 모든 상태 변경은 트랜잭션 경계 안에서 실행한다")
    void commandServiceUsesTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                TaskCommandService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    @DisplayName("Task 생성은 전달받은 domain을 저장한다")
    void givenTask_whenCreateTask_thenSavesTask() {
        // given
        Task task = task("할 일");
        when(taskRepository.save(task)).thenReturn(task);

        // when
        Task created = taskCommandService.createTask(task);

        // then
        assertThat(created).isSameAs(task);
    }

    @Test
    @DisplayName("Task 완료 Command는 완료 상태와 시각을 변경하고 flush한다")
    void givenUncompletedTask_whenCompleteTask_thenChangesStateAndFlushes() {
        // given
        Task task = task("할 일");
        Instant completedAt = Instant.parse("2026-08-09T01:00:00Z");

        // when
        taskCommandService.completeTask(task, completedAt);

        // then
        assertThat(task.isCompleted()).isTrue();
        assertThat(task.getCompletedAt()).isEqualTo(completedAt);
        verify(taskRepository).flush();
    }

    @Test
    @DisplayName("Task 미완료 Command는 완료 상태와 시각을 초기화하고 flush한다")
    void givenCompletedTask_whenUncompleteTask_thenClearsStateAndFlushes() {
        // given
        Task task = task("할 일");
        task.changeCompleted(Instant.parse("2026-08-09T01:00:00Z"));

        // when
        taskCommandService.uncompleteTask(task);

        // then
        assertThat(task.isCompleted()).isFalse();
        assertThat(task.getCompletedAt()).isNull();
        verify(taskRepository).flush();
    }

    @Test
    @DisplayName("Task 제목 변경 Command는 제목을 변경하고 flush한다")
    void givenTask_whenUpdateTaskTitle_thenChangesTitleAndFlushes() {
        // given
        Task task = task("기존 제목");

        // when
        taskCommandService.updateTaskTitle(task, "변경 제목");

        // then
        assertThat(task.getTaskTitle()).isEqualTo("변경 제목");
        verify(taskRepository).flush();
    }

    @Test
    @DisplayName("완료 Task cleanup Command는 cutoff 이전 삭제 결과를 반환한다")
    void givenCutoff_whenDeleteCompletedTasksBefore_thenReturnsDeletedCount() {
        // given
        Instant cutoff = Instant.parse("2026-07-10T00:00:00Z");
        when(taskRepository.deleteCompletedTasksBefore(cutoff)).thenReturn(3);

        // when
        int deletedCount = taskCommandService.deleteCompletedTasksBefore(cutoff);

        // then
        assertThat(deletedCount).isEqualTo(3);
    }

    private Task task(String title) {
        return new Task(title, new Account());
    }
}
