package com.calio.calendar.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.service.TaskService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskCleanupSchedulerTest {

    @Mock
    private TaskService taskService;

    @Test
    @DisplayName("task cleanup scheduler는 실행 시각 기준 30일 이전 완료 작업 삭제를 service에 위임한다")
    void givenFixedClock_whenDeleteOldCompletedTasks_thenDelegatesCleanupWithThirtyDayCutoff() {
        // given
        Instant now = Instant.parse("2026-07-06T19:30:00Z");
        Instant expectedCutoff = Instant.parse("2026-06-06T19:30:00Z");
        TaskCleanupScheduler scheduler = scheduler(now);
        when(taskService.deleteCompletedTasksOlderThan(expectedCutoff)).thenReturn(3);

        // when
        scheduler.deleteOldCompletedTasks();

        // then
        verify(taskService).deleteCompletedTasksOlderThan(expectedCutoff);
    }

    @Test
    @DisplayName("task cleanup scheduler는 cleanup 실패를 외부로 전파하지 않는다")
    void givenCleanupFailure_whenDeleteOldCompletedTasks_thenContainsException() {
        // given
        Instant now = Instant.parse("2026-07-06T19:30:00Z");
        Instant expectedCutoff = Instant.parse("2026-06-06T19:30:00Z");
        TaskCleanupScheduler scheduler = scheduler(now);
        doThrow(new RuntimeException("cleanup failed"))
                .when(taskService)
                .deleteCompletedTasksOlderThan(expectedCutoff);

        // when, then
        assertDoesNotThrow(scheduler::deleteOldCompletedTasks);
    }

    private TaskCleanupScheduler scheduler(Instant now) {
        return new TaskCleanupScheduler(
                taskService,
                Clock.fixed(now, ZoneId.of("Asia/Seoul"))
        );
    }
}
