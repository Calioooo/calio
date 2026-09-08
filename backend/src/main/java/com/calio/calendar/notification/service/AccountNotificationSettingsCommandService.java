package com.calio.calendar.notification.service;

import com.calio.calendar.notification.domain.AccountNotificationSettings;
import com.calio.calendar.notification.repository.AccountNotificationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountNotificationSettingsCommandService {

    private final AccountNotificationSettingsRepository settingsRepository;

    public AccountNotificationSettingsCommandService(AccountNotificationSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public AccountNotificationSettings create(AccountNotificationSettings settings) {
        return settingsRepository.save(settings);
    }
}
