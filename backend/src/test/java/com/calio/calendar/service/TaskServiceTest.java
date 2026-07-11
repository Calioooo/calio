package com.calio.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.repository.AccountRepository;
import com.calio.calendar.repository.TaskRepository;
import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.Task;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-task-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TaskServiceTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account account;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
        accountRepository.deleteAll();
        account = accountRepository.saveAndFlush(new Account());
    }

    @Test
    @DisplayName("cleanup은 cutoff보다 오래된 완료 작업만 hard-delete한다")
    void givenCompletedAndActiveTasks_whenCleanupOldCompletedTasks_thenDeletesOnlyOldCompletedTasks() {
        // given
        Instant now = Instant.parse("2026-07-06T00:00:00Z");
        Task oldCompletedTask = saveCompletedTask("Old completed", now.minus(31, ChronoUnit.DAYS));
        Task recentCompletedTask = saveCompletedTask("Recent completed", now.minus(29, ChronoUnit.DAYS));
        Task activeTask = taskRepository.saveAndFlush(new Task("Active task", account));

        // when
        int deletedCount = taskService.deleteCompletedTasksOlderThan(now.minus(30, ChronoUnit.DAYS));

        // then
        assertThat(deletedCount).isEqualTo(1);
        assertThat(taskRepository.existsById(oldCompletedTask.getTaskId())).isFalse();
        assertThat(taskRepository.existsById(recentCompletedTask.getTaskId())).isTrue();
        assertThat(taskRepository.existsById(activeTask.getTaskId())).isTrue();
    }

    private Task saveCompletedTask(String taskTitle, Instant completedAt) {
        Task task = new Task(taskTitle, account);
        task.complete(completedAt);
        return taskRepository.saveAndFlush(task);
    }
}
