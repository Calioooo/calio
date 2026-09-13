package com.calio.calendar.notification.scheduler;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.service.CalendarNotificationEvaluationService;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CalendarNotificationScheduler {

    private final AccountQueryService accountQueryService;
    private final CalendarNotificationEvaluationService evaluationService;
    private final boolean schedulerEnabled;

    public CalendarNotificationScheduler(
            AccountQueryService accountQueryService,
            CalendarNotificationEvaluationService evaluationService,
            @Value("${notifications.scheduler-enabled:false}") boolean schedulerEnabled
    ) {
        this.accountQueryService = accountQueryService;
        this.evaluationService = evaluationService;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(cron = "0 * * * * *")
    public void dispatchDueNotifications() {
        if (!schedulerEnabled) {
            return;
        }

        Instant now = Instant.now();
        accountQueryService.listNotificationEnabledAccounts()
                .forEach(account -> evaluationService.evaluate(account, now));
    }
}
