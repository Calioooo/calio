package com.calio.calendar.notification.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.notification.domain.AccountNotificationSettings;
import com.calio.calendar.notification.repository.AccountNotificationSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountNotificationSettingsCommandService {

    private final AccountNotificationSettingsRepository settingsRepository;

    public AccountNotificationSettingsCommandService(AccountNotificationSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AccountNotificationSettings createDefaultSettings(Account account) {
        return settingsRepository.saveAndFlush(new AccountNotificationSettings(account));
    }
}
