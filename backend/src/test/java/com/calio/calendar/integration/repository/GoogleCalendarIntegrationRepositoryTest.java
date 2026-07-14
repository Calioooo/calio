package com.calio.calendar.integration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    @DisplayName("Google Calendar integration은 accountId 기준으로 조회, 존재 확인, 삭제된다")
    void givenIntegration_whenFindExistsAndDeleteByAccountId_thenUsesAccountIdContract() {
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

        googleCalendarIntegrationRepository.deleteByAccountId(account.getId());
        assertThat(googleCalendarIntegrationRepository.existsByAccountId(account.getId())).isFalse();
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
