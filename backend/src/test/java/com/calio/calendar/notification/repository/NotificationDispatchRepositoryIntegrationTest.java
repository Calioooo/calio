package com.calio.calendar.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.notification.domain.CalendarNotificationType;
import com.calio.calendar.notification.domain.NotificationDispatch;
import com.calio.calendar.notification.domain.NotificationDispatchState;
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
        NotificationDispatch.completed(
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
        NotificationDispatch.completed(
            account.getId(),
            CalendarNotificationType.REMINDER,
            NotificationScheduleKey.personalEvent(1L),
            scheduledAt));

    // when & then
    assertThatThrownBy(
            () ->
                dispatchRepository.saveAndFlush(
                    NotificationDispatch.completed(
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
            NotificationDispatch.completed(
                account.getId(),
                CalendarNotificationType.REMINDER,
                NotificationScheduleKey.personalEvent(1L),
                cutoff.minusSeconds(1)));
    NotificationDispatch retained =
        dispatchRepository.saveAndFlush(
            NotificationDispatch.completed(
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

  @Test
  @DisplayName("만료된 processing lease는 새 worker가 획득하고 실패 후 예약 시각부터 재시도한다")
  void givenExpiredProcessingLease_whenRetrying_thenTransfersOwnershipAndHonorsRunnableAt() {
    // given
    Account account = accountRepository.saveAndFlush(new Account());
    Instant now = Instant.parse("2026-09-08T00:00:00Z");
    NotificationDispatch dispatch =
        dispatchRepository.saveAndFlush(
            NotificationDispatch.claimed(
                account.getId(),
                CalendarNotificationType.REMINDER,
                NotificationScheduleKey.personalEvent(1L),
                now,
                "expired-owner",
                now.minusSeconds(1)));

    // when
    int acquired =
        dispatchRepository.tryAcquire(
            dispatch.getId(),
            "new-owner",
            now,
            now.plusSeconds(60),
            NotificationDispatchState.PROCESSING,
            NotificationDispatchState.RETRYABLE);
    int markedRetryable =
        dispatchRepository.markRetryable(
            dispatch.getId(),
            "new-owner",
            now.plusSeconds(60),
            NotificationDispatchState.PROCESSING,
            NotificationDispatchState.RETRYABLE);
    int acquiredTooEarly =
        dispatchRepository.tryAcquire(
            dispatch.getId(),
            "early-owner",
            now.plusSeconds(59),
            now.plusSeconds(120),
            NotificationDispatchState.PROCESSING,
            NotificationDispatchState.RETRYABLE);
    int acquiredWhenDue =
        dispatchRepository.tryAcquire(
            dispatch.getId(),
            "retry-owner",
            now.plusSeconds(60),
            now.plusSeconds(120),
            NotificationDispatchState.PROCESSING,
            NotificationDispatchState.RETRYABLE);

    // then
    assertThat(acquired).isEqualTo(1);
    assertThat(markedRetryable).isEqualTo(1);
    assertThat(acquiredTooEarly).isZero();
    assertThat(acquiredWhenDue).isEqualTo(1);
    assertThat(dispatchRepository.findById(dispatch.getId()))
        .get()
        .satisfies(
            retried -> {
              assertThat(retried.getState()).isEqualTo(NotificationDispatchState.PROCESSING);
              assertThat(retried.getOwnerToken()).isEqualTo("retry-owner");
              assertThat(retried.getRetryCount()).isEqualTo(1);
            });
  }
}
