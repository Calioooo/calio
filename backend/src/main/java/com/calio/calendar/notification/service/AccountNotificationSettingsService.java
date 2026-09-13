package com.calio.calendar.notification.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.service.AccountCommandService;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.controller.dto.UpdateNotificationSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountNotificationSettingsService {

    private final AccountQueryService accountQueryService;
    private final AccountCommandService accountCommandService;

    public AccountNotificationSettingsService(
            AccountQueryService accountQueryService,
            AccountCommandService accountCommandService
    ) {
        this.accountQueryService = accountQueryService;
        this.accountCommandService = accountCommandService;
    }

    public AccountNotificationSettings get(Long accountId) {
        return accountQueryService.getAccount(accountId).getNotificationSettings();
    }

    public AccountNotificationSettings update(
            Long accountId,
            UpdateNotificationSettingsRequest request
    ) {
        Account account = accountQueryService.getAccount(accountId);
        return accountCommandService.updateNotificationSettings(
                account,
                request.calendarNotificationsEnabled(),
                request.timedReminderOffset(),
                request.importantReminderOffset(),
                request.allDayReminderTime(),
                request.dailyBriefingEnabled(),
                request.dailyBriefingTime()
        );
    }
}
