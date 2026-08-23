package com.calio.calendar.integration.connection.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Test
    @DisplayName("periodic sync 대상은 CONNECTED integration으로 제한한다")
    void givenDisconnectedAndSyncErrorIntegrations_whenFindConnectedAccounts_thenExcludesPausedStates() {
        Account connectedAccount = accountRepository.saveAndFlush(new Account());
        Account disconnectedAccount = accountRepository.saveAndFlush(new Account());
        Account syncErrorAccount = accountRepository.saveAndFlush(new Account());
        googleCalendarIntegrationRepository.saveAndFlush(integration(connectedAccount.getId(), "connected"));
        GoogleCalendarIntegration disconnected = googleCalendarIntegrationRepository.saveAndFlush(
                integration(disconnectedAccount.getId(), "disconnected"));
        disconnected.disconnect(Instant.parse("2026-08-23T00:00:00Z"));
        googleCalendarIntegrationRepository.saveAndFlush(disconnected);
        GoogleCalendarIntegration syncError = googleCalendarIntegrationRepository.saveAndFlush(
                integration(syncErrorAccount.getId(), "sync-error"));
        syncError.markSyncError("ACCOUNT_WIDE_INVARIANT", Instant.parse("2026-08-23T00:00:00Z"));
        googleCalendarIntegrationRepository.saveAndFlush(syncError);

        assertThat(googleCalendarIntegrationRepository.findConnectedAccountIdsAfter(0L, PageRequest.of(0, 10)))
                .contains(connectedAccount.getId())
                .doesNotContain(disconnectedAccount.getId(), syncErrorAccount.getId());
    }

    @Test
    @Transactional
    @DisplayName("disconnect 이후 stale worker는 cursor와 access token, operation lease를 갱신하지 못한다")
    void givenDisconnectedIntegration_whenWorkerPersistsProviderState_thenAllFencedUpdatesAreRejected() {
        Account account = accountRepository.saveAndFlush(new Account());
        GoogleCalendarIntegration integration = googleCalendarIntegrationRepository.saveAndFlush(
                integration(account.getId(), "google-subject"));
        integration.disconnect(Instant.parse("2026-08-23T00:00:00Z"));
        googleCalendarIntegrationRepository.saveAndFlush(integration);

        assertThat(googleCalendarIntegrationRepository.updateNextSyncToken(integration.getId(), "stale-cursor"))
                .isZero();
        assertThat(googleCalendarIntegrationRepository.updateAccessToken(
                integration.getId(), "encrypted-refresh-token", "stale-access-token",
                Instant.parse("2026-08-23T01:00:00Z"))).isZero();
        assertThat(googleCalendarIntegrationRepository.acquireGoogleOperationLease(
                account.getId(), "stale-worker", 300)).isZero();
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
