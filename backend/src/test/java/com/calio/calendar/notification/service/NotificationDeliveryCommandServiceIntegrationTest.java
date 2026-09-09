package com.calio.calendar.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calendar-notification-delivery-test;"
                + "MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NotificationDeliveryCommandServiceIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private NotificationDeliveryCommandService deliveryCommandService;

    @Test
    @DisplayName("같은 수신자와 일정 시각의 delivery claim은 한 번만 생성된다")
    void givenExistingDeliveryClaim_whenCreateAgain_thenRejectsDuplicateClaim() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        Instant scheduledAt = Instant.parse("2026-09-08T00:00:00Z");
        deliveryCommandService.create(
                account,
                "REMINDER",
                "personal:1",
                scheduledAt,
                LocalDate.of(2026, 9, 8),
                "회의",
                null
        );

        // when & then
        assertThatThrownBy(() -> deliveryCommandService.create(
                account,
                "REMINDER",
                "personal:1",
                scheduledAt,
                LocalDate.of(2026, 9, 8),
                "회의",
                null
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
