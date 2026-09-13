package com.calio.calendar.notification.service;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.controller.dto.UpdateNotificationSettingsRequest;
import com.calio.calendar.notification.domain.AccountNotificationSettings;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountNotificationSettingsService {

    private final AccountQueryService accountQueryService;
    private final AccountNotificationSettingsQueryService settingsQueryService;
    private final AccountNotificationSettingsCommandService settingsCommandService;

    public AccountNotificationSettingsService(
            AccountQueryService accountQueryService,
            AccountNotificationSettingsQueryService settingsQueryService,
            AccountNotificationSettingsCommandService settingsCommandService
    ) {
        this.accountQueryService = accountQueryService;
        this.settingsQueryService = settingsQueryService;
        this.settingsCommandService = settingsCommandService;
    }

    public AccountNotificationSettings get(Long accountId) {
        return settingsQueryService.getSettingsIfExists(accountId)
                .orElseGet(() -> createDefaultSettings(accountId));
    }

    public AccountNotificationSettings update(
            Long accountId,
            UpdateNotificationSettingsRequest request
    ) {
        AccountNotificationSettings settings = get(accountId);
        settings.update(
                request.calendarNotificationsEnabled(),
                request.timedReminderOffset(),
                request.importantReminderOffset(),
                request.allDayReminderTime(),
                request.dailyBriefingEnabled(),
                request.dailyBriefingTime()
        );
        return settings;
    }

    private AccountNotificationSettings createDefaultSettings(Long accountId) {
        try {
            settingsCommandService.createDefaultSettings(accountQueryService.getAccount(accountId));
        } catch (DataIntegrityViolationException ignored) {
        }
        return settingsQueryService.getSettingsIfExists(accountId)
                .orElseThrow(() -> new IllegalStateException("Notification settings creation failed."));
    }
}
