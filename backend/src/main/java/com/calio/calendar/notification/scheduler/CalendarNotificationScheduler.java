package com.calio.calendar.notification.scheduler;

import com.calio.calendar.notification.usecase.SendDueCalendarNotificationsUseCase;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CalendarNotificationScheduler {

  private final SendDueCalendarNotificationsUseCase sendDueCalendarNotificationsUseCase;
  private final Clock clock;
  private final boolean schedulerEnabled;

  public CalendarNotificationScheduler(
      SendDueCalendarNotificationsUseCase sendDueCalendarNotificationsUseCase,
      Clock clock,
      @Value("${notifications.scheduler-enabled:false}") boolean schedulerEnabled) {
    this.sendDueCalendarNotificationsUseCase = sendDueCalendarNotificationsUseCase;
    this.clock = clock;
    this.schedulerEnabled = schedulerEnabled;
  }

  @Scheduled(cron = "0 * * * * *")
  public void dispatchDueNotifications() {
    if (!schedulerEnabled) {
      return;
    }

    Instant now = clock.instant();
    sendDueCalendarNotificationsUseCase.execute(now);
  }
}
