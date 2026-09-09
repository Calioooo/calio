package com.calio.calendar.notification.scheduler;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.domain.AccountNotificationSettings;
import com.calio.calendar.notification.service.AccountNotificationSettingsService;
import com.calio.calendar.notification.service.CalendarNotificationEvaluationService;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CalendarNotificationScheduler {

    private final AccountQueryService accountQueryService;
    private final AccountNotificationSettingsService settingsService;
    private final CalendarNotificationEvaluationService evaluationService;
    private final boolean schedulerEnabled;

    public CalendarNotificationScheduler(
            AccountQueryService accountQueryService,
            AccountNotificationSettingsService settingsService,
            CalendarNotificationEvaluationService evaluationService,
            @Value("${notifications.scheduler-enabled:false}") boolean schedulerEnabled
    ) {
        this.accountQueryService = accountQueryService;
        this.settingsService = settingsService;
        this.evaluationService = evaluationService;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(cron = "0 * * * * *")
    public void dispatchDueNotifications() {
        if (!schedulerEnabled) {
            return;
        }

        Instant now = Instant.now();
        accountQueryService.listAccounts()
                .forEach(account -> evaluateAccount(account.getId(), now));
    }

    private void evaluateAccount(Long accountId, Instant now) {
        AccountNotificationSettings settings = settingsService.get(accountId);
        if (!settings.isCalendarNotificationsEnabled()) {
            return;
        }

        evaluationService.evaluate(settings, now);
    }
}
