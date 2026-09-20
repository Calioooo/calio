package com.calio.calendar.notification.scheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.notification.usecase.DeleteExpiredNotificationDispatchesUseCase;
import com.calio.calendar.notification.usecase.SendDueCalendarNotificationsUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarNotificationSchedulerTest {

  private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

  @Mock private SendDueCalendarNotificationsUseCase sendDueCalendarNotificationsUseCase;

  @Mock
  private DeleteExpiredNotificationDispatchesUseCase deleteExpiredNotificationDispatchesUseCase;

  @Test
  @DisplayName("dispatch cleanup은 현재 시각보다 1시간 이전을 cutoff로 사용한다")
  void givenEnabledScheduler_whenDeletingExpiredDispatches_thenUsesOneHourRetention() {
    // given
    CalendarNotificationScheduler scheduler = scheduler(true);
    Instant expectedCutoff = Instant.parse("2026-09-21T11:00:00Z");
    when(deleteExpiredNotificationDispatchesUseCase.execute(expectedCutoff)).thenReturn(2);

    // when
    scheduler.deleteExpiredDispatches();

    // then
    verify(deleteExpiredNotificationDispatchesUseCase).execute(expectedCutoff);
  }

  @Test
  @DisplayName("notification scheduler가 비활성화되면 dispatch cleanup을 실행하지 않는다")
  void givenDisabledScheduler_whenDeletingExpiredDispatches_thenSkipsCleanup() {
    // given
    CalendarNotificationScheduler scheduler = scheduler(false);

    // when
    scheduler.deleteExpiredDispatches();

    // then
    verify(deleteExpiredNotificationDispatchesUseCase, never())
        .execute(org.mockito.ArgumentMatchers.any());
  }

  private CalendarNotificationScheduler scheduler(boolean enabled) {
    return new CalendarNotificationScheduler(
        sendDueCalendarNotificationsUseCase,
        deleteExpiredNotificationDispatchesUseCase,
        Clock.fixed(NOW, ZoneOffset.UTC),
        enabled);
  }
}
