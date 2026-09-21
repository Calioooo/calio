package com.calio.calendar.notification.scheduler;

import com.calio.calendar.notification.usecase.DeleteExpiredNotificationDispatchesUseCase;
import com.calio.calendar.notification.usecase.SendDueCalendarNotificationsUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CalendarNotificationScheduler {

  private static final Duration DISPATCH_RETENTION = Duration.ofHours(1);
  private static final Logger log = LoggerFactory.getLogger(CalendarNotificationScheduler.class);

  private final SendDueCalendarNotificationsUseCase sendDueCalendarNotificationsUseCase;
  private final DeleteExpiredNotificationDispatchesUseCase
      deleteExpiredNotificationDispatchesUseCase;
  private final Clock clock;
  private final boolean schedulerEnabled;

  public CalendarNotificationScheduler(
      SendDueCalendarNotificationsUseCase sendDueCalendarNotificationsUseCase,
      DeleteExpiredNotificationDispatchesUseCase deleteExpiredNotificationDispatchesUseCase,
      Clock clock,
      @Value("${notifications.scheduler-enabled:false}") boolean schedulerEnabled) {
    this.sendDueCalendarNotificationsUseCase = sendDueCalendarNotificationsUseCase;
    this.deleteExpiredNotificationDispatchesUseCase = deleteExpiredNotificationDispatchesUseCase;
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

  @Scheduled(cron = "0 0 * * * *")
  public void deleteExpiredDispatches() {
    if (!schedulerEnabled) {
      return;
    }

    Instant cutoff = clock.instant().minus(DISPATCH_RETENTION);
    int deletedCount = deleteExpiredNotificationDispatchesUseCase.execute(cutoff);
    log.info("Notification dispatch cleanup finished. deletedCount={}", deletedCount);
  }
}
