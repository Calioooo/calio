package com.calio.calendar.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarOperationJob;
import com.calio.calendar.integration.domain.GoogleCalendarOperationStatus;
import com.calio.calendar.integration.domain.GoogleCalendarOperationTrigger;
import com.calio.calendar.integration.service.GoogleCalendarOperationCoordinator;
import com.calio.calendar.integration.service.GoogleCalendarOperationCoordinator.ClaimedOperation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-google-integration-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GoogleCalendarIntegrationRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private GoogleCalendarIntegrationRepository googleCalendarIntegrationRepository;

    @Autowired
    private GoogleCalendarOperationJobRepository googleCalendarOperationJobRepository;

    @Autowired
    private GoogleCalendarOperationCoordinator operationCoordinator;

    @Test
    @Transactional
    @DisplayName("Google Calendar integration은 accountId 기준으로 조회되고 존재 여부를 확인할 수 있다")
    void givenIntegration_whenFindAndCheckExists_thenUsesAccountIdContract() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = googleCalendarIntegrationRepository.saveAndFlush(
                integration(account.getId(), "google-subject")
        );

        // when, then
        assertThat(googleCalendarIntegrationRepository.findByAccountId(account.getId()))
                .map(GoogleCalendarIntegration::getId)
                .contains(integration.getId());
        assertThat(googleCalendarIntegrationRepository.existsByAccountId(account.getId())).isTrue();
    }

    @Test
    @DisplayName("Google Calendar integration은 accountId unique constraint로 계정당 단일 row만 허용한다")
    void givenDuplicateAccountId_whenSave_thenUniqueConstraintRejectsDuplicateRow() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        googleCalendarIntegrationRepository.saveAndFlush(integration(account.getId(), "first-subject"));

        // when, then
        assertThatThrownBy(() ->
                googleCalendarIntegrationRepository.saveAndFlush(integration(account.getId(), "second-subject")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    @DisplayName("active lease가 있으면 다른 run의 획득과 해제가 차단된다")
    void givenActiveLease_whenDifferentRunAcquiresOrReleases_thenPreservesOwner() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = googleCalendarIntegrationRepository.saveAndFlush(
                integration(account.getId(), "google-subject")
        );

        // when, then
        assertThat(googleCalendarIntegrationRepository.acquireSyncLease(
                account.getId(),
                "first-run"
        )).isOne();
        assertThat(googleCalendarIntegrationRepository.acquireSyncLease(
                account.getId(),
                "second-run"
        )).isZero();
        assertThat(googleCalendarIntegrationRepository.releaseSyncLease(
                integration.getId(),
                "second-run"
        )).isZero();
        assertThat(googleCalendarIntegrationRepository.releaseSyncLease(
                integration.getId(),
                "first-run"
        )).isOne();
    }

    @Test
    @Transactional
    @DisplayName("만료된 lease의 PROCESSING head는 recovery polling 대상으로 복구된다")
    void givenExpiredProcessingHead_whenFindRunnableAccounts_thenRecoversAccount() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = googleCalendarIntegrationRepository.saveAndFlush(
                integration(account.getId(), "google-subject")
        );
        Instant claimedAt = Instant.now();
        GoogleCalendarOperationJob job = GoogleCalendarOperationJob.sync(
                integration,
                integration.allocateOperationSequence(),
                GoogleCalendarOperationTrigger.MANUAL,
                claimedAt
        );
        job.claim("expired-owner");
        googleCalendarOperationJobRepository.saveAndFlush(job);
        assertThat(googleCalendarIntegrationRepository.acquireSyncLease(
                account.getId(), "expired-owner"
        )).isOne();

        // when
        List<Long> runnableAccounts = googleCalendarOperationJobRepository.findRunnableAccountIds(
                claimedAt.plus(Duration.ofMinutes(6)),
                List.of(
                        GoogleCalendarOperationStatus.PENDING,
                        GoogleCalendarOperationStatus.PROCESSING
                ),
                PageRequest.of(0, 4)
        );

        // then
        assertThat(runnableAccounts).containsExactly(account.getId());
    }

    @Test
    @Transactional
    @DisplayName("conflict outcome은 cursor와 lease를 정리하고 terminal job을 보존한다")
    void givenClaimedSync_whenConflictTerminates_thenCommitsCursorAndTerminalJob() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = googleCalendarIntegrationRepository.saveAndFlush(
                integration(account.getId(), "google-subject")
        );
        GoogleCalendarOperationJob job = googleCalendarOperationJobRepository.saveAndFlush(
                GoogleCalendarOperationJob.sync(
                        integration,
                        integration.allocateOperationSequence(),
                        GoogleCalendarOperationTrigger.MANUAL,
                        Instant.now().minusSeconds(1)
                )
        );
        ClaimedOperation claimed = operationCoordinator.claim(account.getId(), "conflict-owner")
                .orElseThrow();

        // when
        operationCoordinator.terminateConflict(claimed, "next-sync-token");

        // then
        GoogleCalendarOperationJob conflicted = googleCalendarOperationJobRepository
                .findById(job.getId()).orElseThrow();
        GoogleCalendarIntegration finalized = googleCalendarIntegrationRepository
                .findById(integration.getId()).orElseThrow();
        assertThat(conflicted.getStatus()).isEqualTo(GoogleCalendarOperationStatus.CONFLICTED);
        assertThat(conflicted.getTerminalReason()).isEqualTo("SEMANTIC_CONFLICT");
        assertThat(finalized.getNextSyncToken()).isEqualTo("next-sync-token");
        assertThat(finalized.getActiveSyncRunId()).isNull();
        assertThat(finalized.getSyncLeaseExpiresAt()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("access token refresh 저장은 기존 encrypted refresh token을 유지한다")
    void givenRefreshedAccessToken_whenUpdateToken_thenPreservesRefreshToken() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = googleCalendarIntegrationRepository.saveAndFlush(
                integration(account.getId(), "google-subject")
        );

        // when
        int updated = googleCalendarIntegrationRepository.updateAccessToken(
                integration.getId(),
                "encrypted-refresh-token",
                "new-encrypted-access-token",
                Instant.parse("2026-07-14T14:00:00Z")
        );

        // then
        assertThat(updated).isOne();
        GoogleCalendarIntegration refreshed = googleCalendarIntegrationRepository
                .findById(integration.getId())
                .orElseThrow();
        assertThat(refreshed.getEncryptedRefreshToken()).isEqualTo("encrypted-refresh-token");
        assertThat(refreshed.getEncryptedAccessToken()).isEqualTo("new-encrypted-access-token");
        assertThat(refreshed.getAccessTokenExpiresAt())
                .isEqualTo(Instant.parse("2026-07-14T14:00:00Z"));
    }

    private GoogleCalendarIntegration integration(Long accountId, String googleSubject) {
        return new GoogleCalendarIntegration(
                accountId,
                googleSubject,
                "user@example.com",
                "encrypted-refresh-token",
                "encrypted-access-token",
                Instant.parse("2026-07-14T13:00:00Z"),
                Instant.parse("2026-07-14T12:00:00Z")
        );
    }
}
