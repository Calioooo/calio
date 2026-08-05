package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarSyncTarget;
import com.calio.calendar.integration.domain.GoogleContentHash;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.domain.GoogleOperationJobState;
import com.calio.calendar.integration.domain.GoogleOperationJobTrigger;
import com.calio.calendar.integration.domain.GoogleCalendarItemSnapshot;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import com.calio.calendar.integration.service.GoogleOperationJobPersistenceService.GoogleOperationOwnershipLostException;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
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

    @Test
    @DisplayName("반복 일정의 대기 중인 변경 수에는 해당 일정과 예외 일정 쓰기만 포함한다")
    void givenRecurrenceAndOverrideWrites_whenCountingPendingChanges_thenIncludesBoth() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId()));
        String desiredGoogleContentHash = GoogleContentHash.digest("TEST", "desired");
        jobRepository.saveAndFlush(GoogleOperationJob.outbound(
                "master-operation", integration.getId(), account.getId(),
                integration.allocateGoogleOperationSequence(), "MASTER_UPSERT",
                GoogleCalendarSyncTarget.recurrenceEvent(1L), null,
                "{}", desiredGoogleContentHash, Instant.now()));
        jobRepository.saveAndFlush(GoogleOperationJob.outbound(
                "child-operation", integration.getId(), account.getId(),
                integration.allocateGoogleOperationSequence(), "OVERRIDE_UPSERT",
                GoogleCalendarSyncTarget.recurrenceOverride(
                        1L, Instant.parse("2026-08-04T00:00:00Z")), null,
                "{}", desiredGoogleContentHash, Instant.now()));
        jobRepository.saveAndFlush(GoogleOperationJob.outbound(
                "other-child-operation", integration.getId(), account.getId(),
                integration.allocateGoogleOperationSequence(), "OVERRIDE_UPSERT",
                GoogleCalendarSyncTarget.recurrenceOverride(
                        10L, Instant.parse("2026-08-04T00:00:00Z")), null,
                "{}", desiredGoogleContentHash, Instant.now()));
        String overrideKeyPrefix = GoogleCalendarSyncTarget.overrideKeyPrefix(
                1L);

        // when
        long pendingRecurrenceChanges = jobRepository.countPendingRecurrenceChanges(
                account.getId(), integration.getId(), "1",
                overrideKeyPrefix, overrideKeyPrefix.length());

        // then
        assertThat(pendingRecurrenceChanges).isEqualTo(2L);
    }

    @Autowired
    private GoogleOperationJobPersistenceService persistenceService;

    @Autowired
    private GoogleOperationJobRepository jobRepository;

    @Autowired
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private GoogleCalendarEventMappingRepository eventMappingRepository;

    @Test
    @DisplayName("실행 시각이 남은 FIFO head는 뒤의 실행 가능한 Job 선점을 차단한다")
    void givenDelayedAccountHead_whenClaiming_thenDoesNotSkipToLaterJob() {
        // given
        JobFixture fixture = jobs(
                Instant.now().plusSeconds(3_600),
                Instant.now().minusSeconds(60)
        );
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();

        // when
        GoogleOperationJob claimed = persistenceService.claimHead(
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
    @DisplayName("선두 Job 성공 삭제 후에만 다음 account sequence Job을 선점한다")
    void givenTwoRunnableJobs_whenFirstSucceeds_thenClaimsNextInSequence() {
        // given
        JobFixture fixture = jobs(
                Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(60)
        );
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();

        // when
        GoogleOperationJob first = persistenceService.claimHead(
                fixture.accountId(),
                "worker-a"
        );
        persistenceService.succeed(first.getId(), fixture.accountId(), "worker-a");
        GoogleOperationJob second = persistenceService.claimHead(
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
    @DisplayName("retry는 소유한 PROCESSING Job을 지연된 PENDING으로 되돌리고 횟수를 증가시킨다")
    void givenOwnedProcessingJob_whenRetrying_thenSchedulesPendingRetry() {
        // given
        JobFixture fixture = jobs(Instant.now().minusSeconds(60));
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = persistenceService.claimHead(
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
    @DisplayName("소유하지 않은 worker는 retry, terminal, 성공 삭제 상태 전이를 수행할 수 없다")
    void givenDifferentWorkerToken_whenTransitioning_thenRejectsStaleOwner() {
        // given
        JobFixture fixture = jobs(Instant.now().minusSeconds(60));
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = persistenceService.claimHead(
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
    @DisplayName("operation lease를 해제한 worker는 기존 Job ownership을 갱신할 수 없다")
    void givenReleasedOperationLease_whenRenewingOwnership_thenRejectsWorker() {
        // given
        JobFixture fixture = jobs(Instant.now().minusSeconds(60));
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = persistenceService.claimHead(
                fixture.accountId(),
                "worker-a"
        );
        persistenceService.renewAndAssertOwned(
                claimed.getId(),
                fixture.accountId(),
                "worker-a"
        );
        persistenceService.releaseLease(fixture.accountId(), "worker-a");

        // when, then
        assertThatThrownBy(() -> persistenceService.renewAndAssertOwned(
                claimed.getId(),
                fixture.accountId(),
                "worker-a"
        )).isInstanceOf(GoogleOperationOwnershipLostException.class);
    }

    @Test
    @DisplayName("소유한 PROCESSING Job은 terminal 상태와 실패 사유를 저장한다")
    void givenOwnedProcessingJob_whenTerminating_thenStoresTerminalState() {
        // given
        JobFixture fixture = jobs(Instant.now().minusSeconds(60));
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = persistenceService.claimHead(
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

    @Test
    @DisplayName("매핑 충돌을 발견한 동기화 작업은 삭제하지 않고 충돌 상태로 남긴다")
    void givenMappingConflict_whenSyncSucceeds_thenKeepsConflictedJob() {
        // given
        JobFixture fixture = jobs(Instant.now().minusSeconds(60));
        assertThat(persistenceService.acquireLease(fixture.accountId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = persistenceService.claimHead(
                fixture.accountId(), "worker-a"
        );
        persistenceService.markMappingConflict(
                claimed.getId(), fixture.accountId(), "worker-a"
        );

        // when
        persistenceService.succeed(claimed.getId(), fixture.accountId(), "worker-a");

        // then
        assertThat(jobRepository.findById(claimed.getId()))
                .get()
                .satisfies(conflicted -> {
                    assertThat(conflicted.getState())
                            .isEqualTo(GoogleOperationJobState.CONFLICTED);
                    assertThat(conflicted.getOwnerToken()).isNull();
                    assertThat(conflicted.getLastErrorReason())
                            .isEqualTo("MAPPING_CONFLICT_DETECTED");
                    assertThat(conflicted.isMappingConflictDetected()).isTrue();
                });
    }

    @Test
    @DisplayName("매핑이 이미 충돌 상태면 해당 Google 쓰기 작업을 건너뛴 상태로 종료한다")
    void givenConflictedMapping_whenSkippingGoogleWrite_thenStoresSkippedState() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(new Tag(
                TagType.DEFAULT, "기타", "#64748B"
        ));
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(
                integration(account.getId())
        );
        Event event = eventRepository.saveAndFlush(new Event(
                "Event",
                null,
                Instant.parse("2026-08-05T00:00:00Z"),
                Instant.parse("2026-08-05T01:00:00Z"),
                false,
                "UTC",
                null,
                tag,
                account
        ));
        GoogleCalendarEventMapping mapping = new GoogleCalendarEventMapping(
                integration,
                event,
                "external-event",
                new GoogleCalendarItemSnapshot(
                        "etag",
                        Instant.parse("2026-08-05T01:00:00Z"),
                        GoogleCalendarContentHasher.hashEvent(event)
                )
        );
        mapping.markConflicted();
        eventMappingRepository.saveAndFlush(mapping);
        GoogleOperationJob outbound = jobRepository.saveAndFlush(
                GoogleOperationJob.outbound(
                        "outbound-operation",
                        integration.getId(),
                        account.getId(),
                        integration.allocateGoogleOperationSequence(),
                        "EVENT_UPSERT",
                        GoogleCalendarSyncTarget.event(event.getId()),
                        "external-event",
                        "{}",
                        GoogleContentHash.digest("TEST", "desired"),
                        Instant.now().minusSeconds(60)
                )
        );
        assertThat(persistenceService.acquireLease(account.getId(), "worker-a")).isTrue();
        GoogleOperationJob claimed = persistenceService.claimHead(
                account.getId(), "worker-a"
        );

        // when
        boolean skipped = persistenceService.skipIfTargetConflicted(claimed, "worker-a");

        // then
        assertThat(skipped).isTrue();
        assertThat(jobRepository.findById(outbound.getId()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getState()).isEqualTo(GoogleOperationJobState.SKIPPED);
                    assertThat(saved.getOwnerToken()).isNull();
                    assertThat(saved.getLastErrorReason())
                            .isEqualTo("MAPPING_ALREADY_CONFLICTED");
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
