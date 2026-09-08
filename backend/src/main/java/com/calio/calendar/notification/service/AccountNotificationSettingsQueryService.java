package com.calio.calendar.notification.service;

import com.calio.calendar.notification.domain.AccountNotificationSettings;
import com.calio.calendar.notification.repository.AccountNotificationSettingsRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountNotificationSettingsQueryService {

    private final AccountNotificationSettingsRepository settingsRepository;

    public AccountNotificationSettingsQueryService(AccountNotificationSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public Optional<AccountNotificationSettings> getSettingsIfExists(Long accountId) {
        return settingsRepository.findByAccount_Id(accountId);
    }
}
