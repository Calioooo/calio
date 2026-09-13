package com.calio.calendar.notification.scheduler;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.service.CalendarNotificationService;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CalendarNotificationScheduler {

    private final AccountQueryService accountQueryService;
    private final CalendarNotificationService calendarNotificationService;
    private final boolean schedulerEnabled;

    public CalendarNotificationScheduler(
            AccountQueryService accountQueryService,
            CalendarNotificationService calendarNotificationService,
            @Value("${notifications.scheduler-enabled:false}") boolean schedulerEnabled
    ) {
        this.accountQueryService = accountQueryService;
        this.calendarNotificationService = calendarNotificationService;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(cron = "0 * * * * *")
    public void dispatchDueNotifications() {
        if (!schedulerEnabled) {
            return;
        }

        Instant now = Instant.now();
        accountQueryService.listNotificationEnabledAccounts()
                .forEach(account -> calendarNotificationService.dispatchDueNotifications(account, now));
    }
}
