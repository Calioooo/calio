package com.calio.calendar.integration.sync.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobTrigger;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.sync.operation.repository.GoogleOperationJobRepository;
import com.calio.calendar.integration.sync.operation.GoogleOperationProducerTransaction.OutboundJobDraft;
import com.calio.calendar.task.domain.Task;
import com.calio.calendar.task.repository.TaskRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:google-operation-producer-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GoogleOperationProducerTransactionTest {

    @Autowired
    private GoogleOperationProducerTransaction producerTransaction;

    @Autowired
    private GoogleOperationJobRepository jobRepository;

    @Autowired
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TaskRepository taskRepository;

    @MockitoBean
    private GoogleOperationWorker worker;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        integrationRepository.deleteAll();
        taskRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("canonical mutation과 outbound Job은 같은 transaction으로 commit한 뒤 worker를 깨운다")
    void givenConnectedAccount_whenMutating_thenCommitsMutationAndJobBeforeWake() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );

        // when
        Task task = producerTransaction.mutate(
                account.getId(),
                () -> taskRepository.save(new Task("Google 반영 작업", account)),
                ignored -> outboundDraft()
        );

        // then
        assertThat(taskRepository.findById(task.getTaskId())).isPresent();
        assertThat(jobRepository.findAll())
                .singleElement()
                .satisfies(job -> {
                    assertThat(job.getIntegrationId()).isEqualTo(integration.getId());
                    assertThat(job.getAccountId()).isEqualTo(account.getId());
                    assertThat(job.getAccountSequence()).isEqualTo(1L);
                    assertThat(job.getKind()).isEqualTo("EVENT_UPSERT");
                    assertThat(job.getTrigger())
                            .isEqualTo(GoogleOperationJobTrigger.CANONICAL_MUTATION);
                    assertThat(job.getState()).isEqualTo(GoogleOperationJobState.PENDING);
                });
        verify(worker).wake(account.getId());
    }

    @Test
    @DisplayName("outbound Job 생성이 실패하면 앞선 canonical mutation도 rollback한다")
    void givenInvalidOutboundDraft_whenMutating_thenRollsBackCanonicalMutation() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        integrationRepository.saveAndFlush(integration(account.getId()));
        OutboundJobDraft invalidDraft = new OutboundJobDraft(
                GoogleOperationJob.SYNC_KIND,
                GoogleCalendarEffectiveScope.event(1L),
                null,
                "{}",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );

        // when, then
        assertThatThrownBy(() -> producerTransaction.mutate(
                account.getId(),
                () -> taskRepository.save(new Task("rollback 대상", account)),
                ignored -> invalidDraft
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(taskRepository.count()).isZero();
        assertThat(jobRepository.count()).isZero();
        verify(worker, never()).wake(account.getId());
    }

    @Test
    @DisplayName("canonical mutation이 실패하면 outbound Job을 생성하거나 worker를 깨우지 않는다")
    void givenCanonicalMutationFailure_whenMutating_thenDoesNotCreateJobOrWakeWorker() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        integrationRepository.saveAndFlush(integration(account.getId()));

        // when, then
        assertThatThrownBy(() -> producerTransaction.mutate(
                account.getId(),
                () -> {
                    throw new IllegalStateException("mutation failed");
                },
                ignored -> outboundDraft()
        )).isInstanceOf(IllegalStateException.class);
        assertThat(jobRepository.count()).isZero();
        verify(worker, never()).wake(account.getId());
    }

    @Test
    @DisplayName("Google 연결이 없으면 canonical mutation만 commit하고 outbound Job은 생성하지 않는다")
    void givenNoGoogleIntegration_whenMutating_thenCommitsOnlyCanonicalMutation() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());

        // when
        Task task = producerTransaction.mutate(
                account.getId(),
                () -> taskRepository.save(new Task("로컬 전용 작업", account)),
                ignored -> outboundDraft()
        );

        // then
        assertThat(taskRepository.findById(task.getTaskId())).isPresent();
        assertThat(jobRepository.count()).isZero();
        verify(worker, never()).wake(account.getId());
    }

    private OutboundJobDraft outboundDraft() {
        return new OutboundJobDraft(
                "EVENT_UPSERT",
                GoogleCalendarEffectiveScope.event(1L),
                null,
                "{}",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
    }

    private GoogleCalendarIntegration integration(Long accountId) {
        return new GoogleCalendarIntegration(
                accountId,
                "google-subject",
                "user@example.com",
                "encrypted-refresh-token",
                "encrypted-access-token",
                Instant.now().plusSeconds(3_600),
                Instant.now()
        );
    }
}
