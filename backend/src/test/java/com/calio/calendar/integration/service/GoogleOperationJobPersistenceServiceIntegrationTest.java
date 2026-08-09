package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.domain.GoogleOperationJobState;
import com.calio.calendar.integration.domain.GoogleOperationJobTrigger;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:google-operation-persistence-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class GoogleOperationJobPersistenceServiceIntegrationTest {

    @Autowired
    private GoogleOperationJobPersistenceService persistenceService;

    @Autowired
    private GoogleOperationJobRepository jobRepository;

    @Autowired
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("앞선 Job의 실행 시각이 남아 있으면 뒤의 실행 가능한 Job을 먼저 실행하지 않는다")
    void givenEarlierJobScheduledForLater_whenLaterJobIsRunnable_thenDoesNotRunLaterJob() {
        // given
        JobFixture fixture = jobs(
                Instant.now().plusSeconds(3_600),
                Instant.now().minusSeconds(60)
        );
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();

        // when
        GoogleOperationJob claimed = persistenceService.claimNextJob(
                fixture.accountId(),
                "worker-a"
        );

        // then
        assertThat(claimed).isNull();
        assertThat(jobRepository.findById(fixture.firstJobId()))
                .get()
                .extracting(GoogleOperationJob::getState)
                .isEqualTo(GoogleOperationJobState.PENDING);
        assertThat(jobRepository.findById(fixture.secondJobId()))
                .get()
                .extracting(GoogleOperationJob::getState)
                .isEqualTo(GoogleOperationJobState.PENDING);
    }

    @Test
    @DisplayName("앞선 Job의 성공 후에만 다음 순서의 Job을 실행할 수 있다")
    void givenTwoRunnableJobs_whenFirstSucceeds_thenClaimsNextInSequence() {
        // given
        JobFixture fixture = jobs(
                Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(60)
        );
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();

        // when
        GoogleOperationJob first = persistenceService.claimNextJob(
                fixture.accountId(),
                "worker-a"
        );
        persistenceService.succeed(first.getId(), fixture.accountId(), "worker-a");
        GoogleOperationJob second = persistenceService.claimNextJob(
                fixture.accountId(),
                "worker-a"
        );

        // then
        assertThat(first.getId()).isEqualTo(fixture.firstJobId());
        assertThat(jobRepository.findById(fixture.firstJobId())).isEmpty();
        assertThat(second.getId()).isEqualTo(fixture.secondJobId());
        assertThat(second.getState()).isEqualTo(GoogleOperationJobState.PROCESSING);
        assertThat(second.getOwnerToken()).isEqualTo("worker-a");
    }

    @Test
    @DisplayName("retry 시 현재 진행중인 Job을 PENDING 상태로 변경하고 retry count를 증가시킨다")
    void givenOwnedProcessingJob_whenRetrying_thenSchedulesPendingRetry() {
        // given
        JobFixture fixture = jobs(Instant.now().minusSeconds(60));
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = persistenceService.claimNextJob(
                fixture.accountId(),
                "worker-a"
        );
        Instant beforeRetry = Instant.now();

        // when
        persistenceService.retry(claimed, "worker-a", "TRANSIENT_FAILURE");

        // then
        assertThat(jobRepository.findById(claimed.getId()))
                .get()
                .satisfies(retried -> {
                    assertThat(retried.getState()).isEqualTo(GoogleOperationJobState.PENDING);
                    assertThat(retried.getOwnerToken()).isNull();
                    assertThat(retried.getRetryCount()).isOne();
                    assertThat(retried.getRunnableAt()).isAfter(beforeRetry);
                    assertThat(retried.getLastErrorReason()).isEqualTo("TRANSIENT_FAILURE");
                });
    }

    @Test
    @DisplayName("lease를 얻지 않은 worker가 retry, terminal, succecd 요청 시 OwnershipLostException 예외를 반환한다")
    void givenDifferentWorkerToken_whenTransitioning_thenRejectsStaleOwner() {
        // given
        JobFixture fixture = jobs(Instant.now().minusSeconds(60));
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = persistenceService.claimNextJob(
                fixture.accountId(),
                "worker-a"
        );

        // when, then
        assertThatThrownBy(() -> persistenceService.retry(
                claimed,
                "worker-b",
                "TRANSIENT_FAILURE"
        )).isInstanceOf(GoogleOperationOwnershipLostException.class);
        assertThatThrownBy(() -> persistenceService.terminate(
                claimed.getId(),
                fixture.accountId(),
                "worker-b",
                "PERMANENT_FAILURE"
        )).isInstanceOf(GoogleOperationOwnershipLostException.class);
        assertThatThrownBy(() -> persistenceService.succeed(
                claimed.getId(),
                fixture.accountId(),
                "worker-b"
        )).isInstanceOf(GoogleOperationOwnershipLostException.class);
        assertThat(jobRepository.findById(claimed.getId()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getState()).isEqualTo(GoogleOperationJobState.PROCESSING);
                    assertThat(saved.getOwnerToken()).isEqualTo("worker-a");
                });
    }

    @Test
    @DisplayName("lease를 해제한 worker는 기존 lease을 갱신할 수 없다")
    void givenReleasedOperationLease_whenRenewingOwnership_thenRejectsWorker() {
        // given
        JobFixture fixture = jobs(Instant.now().minusSeconds(60));
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = persistenceService.claimNextJob(
                fixture.accountId(),
                "worker-a"
        );
        persistenceService.extendOperationLease(
                claimed.getId(),
                fixture.accountId(),
                "worker-a"
        );
        persistenceService.releaseLease(fixture.accountId(), "worker-a");

        // when, then
        assertThatThrownBy(() -> persistenceService.extendOperationLease(
                claimed.getId(),
                fixture.accountId(),
                "worker-a"
        )).isInstanceOf(GoogleOperationOwnershipLostException.class);
    }

    @Test
    @DisplayName("terminal 요청 시 해당 job에 terminal 상태와 실패 사유를 저장한다")
    void givenOwnedProcessingJob_whenTerminating_thenStoresTerminalState() {
        // given
        JobFixture fixture = jobs(Instant.now().minusSeconds(60));
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = persistenceService.claimNextJob(
                fixture.accountId(),
                "worker-a"
        );

        // when
        persistenceService.terminate(
                claimed.getId(),
                fixture.accountId(),
                "worker-a",
                "PERMANENT_FAILURE"
        );

        // then
        assertThat(jobRepository.findById(claimed.getId()))
                .get()
                .satisfies(terminated -> {
                    assertThat(terminated.getState()).isEqualTo(GoogleOperationJobState.SYNC_ERROR);
                    assertThat(terminated.getOwnerToken()).isNull();
                    assertThat(terminated.getLastErrorReason()).isEqualTo("PERMANENT_FAILURE");
                });
    }

    private JobFixture jobs(Instant... runnableTimes) {
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        Long firstJobId = null;
        Long secondJobId = null;
        for (int index = 0; index < runnableTimes.length; index++) {
            GoogleOperationJob job = jobRepository.saveAndFlush(GoogleOperationJob.sync(
                    "operation-" + index,
                    integration.getId(),
                    account.getId(),
                    integration.allocateGoogleOperationSequence(),
                    GoogleOperationJobTrigger.MANUAL,
                    runnableTimes[index]
            ));
            if (index == 0) {
                firstJobId = job.getId();
            } else if (index == 1) {
                secondJobId = job.getId();
            }
        }
        return new JobFixture(account.getId(), firstJobId, secondJobId);
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

    private record JobFixture(
            Long accountId,
            Long firstJobId,
            Long secondJobId
    ) {
    }
}
