package com.calio.calendar.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.NotificationDispatch;
import com.calio.calendar.notification.domain.NotificationScheduleKey;
import com.calio.calendar.notification.repository.NotificationDispatchRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:calendar-notification-dispatch-test;MODE=MySQL",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class NotificationDispatchRepositoryIntegrationTest {

  @Autowired private AccountRepository accountRepository;

  @Autowired private NotificationDispatchRepository dispatchRepository;

  @Test
  @DisplayName("같은 수신자와 일정 시각의 dispatch claim은 한 번만 생성된다")
  void givenExistingDispatchClaim_whenCreateAgain_thenRejectsDuplicateClaim() {
    // given
    Account account = accountRepository.saveAndFlush(new Account());
    Instant scheduledAt = Instant.parse("2026-09-08T00:00:00Z");
    dispatchRepository.saveAndFlush(
        new NotificationDispatch(
            account.getId(),
            CalendarNotificationType.REMINDER,
            NotificationScheduleKey.personalEvent(1L),
            scheduledAt));

    // when & then
    assertThatThrownBy(
            () ->
                dispatchRepository.saveAndFlush(
                    new NotificationDispatch(
                        account.getId(),
                        CalendarNotificationType.REMINDER,
                        NotificationScheduleKey.personalEvent(1L),
                        scheduledAt)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
