package com.calio.calendar.task.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.task.domain.Task;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-task-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class TaskRepositoryTest {

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
    @DisplayName("미완료 Task 조회는 account 범위 안에서 taskId 오름차순으로 반환한다")
    void givenTasksAcrossAccountsAndStates_whenFindUncompleted_thenScopesAndSortsResults() {
        // given
        Task first = taskRepository.saveAndFlush(new Task("첫 번째", account));
        Task second = taskRepository.saveAndFlush(new Task("두 번째", account));
        saveCompletedTask("완료", account, Instant.parse("2026-08-01T00:00:00Z"));
        Account otherAccount = accountRepository.saveAndFlush(new Account());
        taskRepository.saveAndFlush(new Task("다른 계정", otherAccount));
        PageRequest pageRequest = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "taskId"));

        // when
        var tasks = taskRepository.findByAccount_IdAndCompletedFalse(account.getId(), pageRequest);

        // then
        assertThat(tasks.getContent())
                .extracting(Task::getTaskId)
                .containsExactly(first.getTaskId(), second.getTaskId());
    }

    @Test
    @Transactional
    @DisplayName("완료 Task 삭제는 cutoff보다 이전인 row만 삭제하고 경계 시각은 보존한다")
    void givenCompletedTasksAroundCutoff_whenDeleteBefore_thenUsesExclusiveBoundary() {
        // given
        Instant cutoff = Instant.parse("2026-07-10T00:00:00Z");
        Task before = saveCompletedTask("이전", account, cutoff.minusSeconds(1));
        Task atCutoff = saveCompletedTask("경계", account, cutoff);
        Task after = saveCompletedTask("이후", account, cutoff.plusSeconds(1));
        Task active = taskRepository.saveAndFlush(new Task("미완료", account));

        // when
        int deletedCount = taskRepository.deleteCompletedTasksBefore(cutoff);

        // then
        assertThat(deletedCount).isOne();
        assertThat(taskRepository.existsById(before.getTaskId())).isFalse();
        assertThat(taskRepository.existsById(atCutoff.getTaskId())).isTrue();
        assertThat(taskRepository.existsById(after.getTaskId())).isTrue();
        assertThat(taskRepository.existsById(active.getTaskId())).isTrue();
    }

    private Task saveCompletedTask(String title, Account owner, Instant completedAt) {
        Task task = new Task(title, owner);
        task.changeCompleted(completedAt);
        return taskRepository.saveAndFlush(task);
    }
}
