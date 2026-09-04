package com.calio.calendar.integration.sync.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarSyncJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobTrigger;
import com.calio.calendar.integration.connection.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.connection.repository.GoogleCalendarConnectionRepository;
import com.calio.calendar.integration.sync.operation.repository.GoogleOperationJobRepository;
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
class GoogleOperationJobServiceIntegrationTest {

    @Autowired
    private GoogleOperationJobService jobService;

    @Autowired
    private GoogleOperationLeaseService leaseService;

    @Autowired
    private GoogleOperationJobRepository jobRepository;

    @Autowired
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private GoogleCalendarConnectionRepository connectionRepository;

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
        assertThat(leaseService.acquire(fixture.accountId(), "worker-a")).isTrue();

        // when
        GoogleOperationJob claimed = jobService.claimNextJob(
                fixture.accountId(), fixture.integrationId(),
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
        assertThat(leaseService.acquire(fixture.accountId(), "worker-a")).isTrue();

        // when
        GoogleOperationJob first = jobService.claimNextJob(
                fixture.accountId(), fixture.integrationId(),
                "worker-a"
        );
        jobService.succeed(first.getId(), fixture.accountId(), "worker-a");
        GoogleOperationJob second = jobService.claimNextJob(
                fixture.accountId(), fixture.integrationId(),
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
        assertThat(leaseService.acquire(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = jobService.claimNextJob(
                fixture.accountId(), fixture.integrationId(),
                "worker-a"
        );
        Instant beforeRetry = Instant.now();

        // when
        jobService.retry(claimed, "worker-a", "TRANSIENT_FAILURE");

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
        assertThat(leaseService.acquire(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = jobService.claimNextJob(
                fixture.accountId(), fixture.integrationId(),
                "worker-a"
        );

        // when, then
        assertThatThrownBy(() -> jobService.retry(
                claimed,
                "worker-b",
                "TRANSIENT_FAILURE"
        )).isInstanceOf(GoogleOperationOwnershipLostException.class);
        assertThatThrownBy(() -> jobService.terminate(
                claimed.getId(),
                fixture.accountId(),
                "worker-b",
                "PERMANENT_FAILURE"
        )).isInstanceOf(GoogleOperationOwnershipLostException.class);
        assertThatThrownBy(() -> jobService.succeed(
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
        assertThat(leaseService.acquire(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = jobService.claimNextJob(
                fixture.accountId(), fixture.integrationId(),
                "worker-a"
        );
        leaseService.extend(
                claimed.getId(),
                fixture.accountId(),
                "worker-a"
        );
        leaseService.release(fixture.accountId(), "worker-a");

        // when, then
        assertThatThrownBy(() -> leaseService.extend(
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
        assertThat(leaseService.acquire(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = jobService.claimNextJob(
                fixture.accountId(), fixture.integrationId(),
                "worker-a"
        );

        // when
        jobService.terminate(
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
        GoogleCalendarConnection connection = connection(account.getId());
        Long firstJobId = null;
        Long secondJobId = null;
        for (int index = 0; index < runnableTimes.length; index++) {
            GoogleOperationJob job = jobRepository.saveAndFlush(GoogleCalendarSyncJob.create(
                    "operation-" + index,
                    connection.getIntegration().getId(),
                    account.getId(),
                    connection.getIntegration().allocateGoogleOperationSequence(),
                    GoogleOperationJobTrigger.MANUAL,
                    runnableTimes[index]
            ));
            if (index == 0) {
                firstJobId = job.getId();
            } else if (index == 1) {
                secondJobId = job.getId();
            }
        }
        return new JobFixture(
                account.getId(),
                connection.getIntegration().getId(),
                firstJobId,
                secondJobId
        );
    }

    private GoogleCalendarConnection connection(Long accountId) {
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(new GoogleCalendarIntegration(accountId));
        return connectionRepository.saveAndFlush(new GoogleCalendarConnection(
                integration,
                "google-subject",
                "user@example.com",
                "encrypted-refresh-token",
                "encrypted-access-token",
                Instant.now().plusSeconds(3_600),
                Instant.now()
        ));
    }

    private record JobFixture(
            Long accountId,
            Long integrationId,
            Long firstJobId,
            Long secondJobId
    ) {
    }
}
