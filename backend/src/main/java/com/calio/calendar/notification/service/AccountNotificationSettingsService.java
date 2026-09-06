package com.calio.calendar.notification.service;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.domain.AccountNotificationSettings;
import com.calio.calendar.notification.repository.AccountNotificationSettingsRepository;
import java.time.LocalTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountNotificationSettingsService {

    private final AccountNotificationSettingsRepository settingsRepository;
    private final AccountQueryService accountQueryService;

    public AccountNotificationSettingsService(
            AccountNotificationSettingsRepository settingsRepository,
            AccountQueryService accountQueryService
    ) {
        this.settingsRepository = settingsRepository;
        this.accountQueryService = accountQueryService;
    }

    public AccountNotificationSettings get(Long accountId) {
        return settingsRepository.findByAccount_Id(accountId)
                .orElseGet(() -> createDefaultSettings(accountId));
    }

    public AccountNotificationSettings update(
            Long accountId,
            boolean calendarNotificationsEnabled,
            Integer timedReminderMinutes,
            Integer importantReminderMinutes,
            LocalTime allDayReminderTime,
            boolean dailyBriefingEnabled,
            LocalTime dailyBriefingTime
    ) {
        AccountNotificationSettings settings = get(accountId);
        settings.update(
                calendarNotificationsEnabled,
                timedReminderMinutes,
                importantReminderMinutes,
                allDayReminderTime,
                dailyBriefingEnabled,
                dailyBriefingTime
        );
        return settings;
    }

    private AccountNotificationSettings createDefaultSettings(Long accountId) {
        AccountNotificationSettings settings = new AccountNotificationSettings(
                accountQueryService.getAccount(accountId)
        );
        return settingsRepository.save(settings);
    }
}
