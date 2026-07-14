package com.calio.calendar.integration.googlecalendar.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.integration.googlecalendar.domain.GoogleCalendarIntegration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-google-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GoogleCalendarIntegrationRepositoryTest {

    @Autowired
    private GoogleCalendarIntegrationRepository integrationRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Account account;

    @BeforeEach
    void setUp() {
        integrationRepository.deleteAll();
        accountRepository.deleteAll();
        account = accountRepository.saveAndFlush(new Account());
    }

    @Test
    @Transactional
    @DisplayName("accountId로 Google Calendar Integration을 조회하고 삭제할 수 있다")
    void givenIntegration_whenFindAndDeleteByAccountId_thenUsesAccountScopedRow() {
        // given
        GoogleCalendarIntegration integration = integrationRepository.saveAndFlush(newIntegration(account.getId()));

        // when then
        assertThat(integrationRepository.findByAccountId(account.getId()))
                .hasValueSatisfying(foundIntegration -> {
                    assertThat(foundIntegration.getGoogleCalendarIntegrationId())
                            .isEqualTo(integration.getGoogleCalendarIntegrationId());
                    assertThat(foundIntegration.getAccountId()).isEqualTo(account.getId());
                });
        assertThat(integrationRepository.existsByAccountId(account.getId())).isTrue();

        integrationRepository.deleteByAccountId(account.getId());
        integrationRepository.flush();

        assertThat(integrationRepository.existsByAccountId(account.getId())).isFalse();
    }

    @Test
    @DisplayName("Google Calendar Integration은 account당 하나만 저장할 수 있다")
    void givenDuplicateAccountIntegration_whenSave_thenFailsByUniqueAccountId() {
        // given
        integrationRepository.saveAndFlush(newIntegration(account.getId()));

        // when then
        assertThatThrownBy(() -> integrationRepository.saveAndFlush(newIntegration(account.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private GoogleCalendarIntegration newIntegration(Long accountId) {
        Instant connectedAt = Instant.parse("2026-07-14T00:00:00Z");
        return new GoogleCalendarIntegration(
                accountId,
                "google-subject",
                "user@example.com",
                "v1:1:nonce:ciphertext:tag",
                "access-token",
                connectedAt.plusSeconds(3600),
                connectedAt
        );
    }
}
