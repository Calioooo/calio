package com.calio.calendar.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.task.domain.Task;
import com.calio.calendar.task.repository.TaskRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TaskQueryServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskQueryService taskQueryService;

    @Test
    @DisplayName("QueryService의 모든 조회는 readOnly 트랜잭션 경계 안에서 실행한다")
    void queryServiceUsesReadOnlyTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                TaskQueryService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    @DisplayName("Task 목록은 전달받은 조건으로 account의 미완료 row를 조회해 domain 결과를 반환한다")
    void givenAccountAndPageable_whenListTasks_thenReturnsRepositoryContent() {
        // given
        Task first = task(10L, "첫 번째");
        Task second = task(20L, "두 번째");
        Pageable pageable = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "taskId"));
        when(taskRepository.findByAccount_IdAndCompletedFalse(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        // when
        List<Task> result = taskQueryService.listTasks(1L, pageable);

        // then
        verify(taskRepository).findByAccount_IdAndCompletedFalse(1L, pageable);
        assertThat(result).containsExactly(first, second);
    }

    @Test
    @DisplayName("Task 단건 조회는 taskId와 accountId가 모두 일치하는 row를 반환한다")
    void givenOwnedTask_whenFindTask_thenReturnsTask() {
        // given
        Task task = task(10L, "할 일");
        when(taskRepository.findByTaskIdAndAccount_Id(10L, 1L)).thenReturn(Optional.of(task));

        // when
        Task found = taskQueryService.findTask(1L, 10L);

        // then
        assertThat(found).isSameAs(task);
    }

    @Test
    @DisplayName("account 범위에 Task가 없으면 TASK_NOT_FOUND를 반환한다")
    void givenMissingOwnedTask_whenFindTask_thenThrowsTaskNotFound() {
        // given
        when(taskRepository.findByTaskIdAndAccount_Id(10L, 1L)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> taskQueryService.findTask(1L, 10L))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TASK_NOT_FOUND)
                );
    }

    private Task task(Long taskId, String title) {
        Task task = new Task(title, new Account());
        ReflectionTestUtils.setField(task, "taskId", taskId);
        return task;
    }
}
