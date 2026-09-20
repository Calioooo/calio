package com.calio.calendar.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.NotificationDispatch;
import com.calio.calendar.notification.domain.NotificationScheduleKey;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

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

  @BeforeEach
  void clearDispatches() {
    dispatchRepository.deleteAll();
  }

  @Test
  @DisplayName("동일한 계정과 일정 알림 시각으로 생성된 dispatch claim을 조회한다")
  void givenStoredDispatchClaim_whenCheckClaim_thenReturnsExistence() {
    // given
    Account account = accountRepository.saveAndFlush(new Account());
    Instant scheduledAt = Instant.parse("2026-09-08T00:00:00Z");
    NotificationScheduleKey scheduleKey = NotificationScheduleKey.personalEvent(1L);
    dispatchRepository.saveAndFlush(
        new NotificationDispatch(
            account.getId(), CalendarNotificationType.REMINDER, scheduleKey, scheduledAt));

    // when & then
    assertThat(
            dispatchRepository.hasDispatchClaim(
                account.getId(), CalendarNotificationType.REMINDER, scheduleKey, scheduledAt))
        .isTrue();
    assertThat(
            dispatchRepository.hasDispatchClaim(
                account.getId(),
                CalendarNotificationType.REMINDER,
                scheduleKey,
                scheduledAt.plusSeconds(60)))
        .isFalse();
  }

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

  @Test
  @Transactional
  @DisplayName("cutoff보다 이전에 예정된 dispatch만 삭제한다")
  void givenDispatchesAroundCutoff_whenDeleteScheduledBefore_thenDeletesOnlyExpiredDispatches() {
    // given
    Account account = accountRepository.saveAndFlush(new Account());
    Instant cutoff = Instant.parse("2026-09-08T01:00:00Z");
    NotificationDispatch expired =
        dispatchRepository.saveAndFlush(
            new NotificationDispatch(
                account.getId(),
                CalendarNotificationType.REMINDER,
                NotificationScheduleKey.personalEvent(1L),
                cutoff.minusSeconds(1)));
    NotificationDispatch retained =
        dispatchRepository.saveAndFlush(
            new NotificationDispatch(
                account.getId(),
                CalendarNotificationType.REMINDER,
                NotificationScheduleKey.personalEvent(2L),
                cutoff));

    // when
    int deletedCount = dispatchRepository.deleteScheduledBefore(cutoff);

    // then
    assertThat(deletedCount).isEqualTo(1);
    assertThat(dispatchRepository.findById(expired.getId())).isEmpty();
    assertThat(dispatchRepository.findById(retained.getId())).isPresent();
  }
}
