package com.calio.calendar.notification.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.notification.controller.dto.UpdateNotificationSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountNotificationSettingsService {

  private final AccountRepository accountRepository;

  public AccountNotificationSettingsService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  public AccountNotificationSettings get(Long accountId) {
    return getAccount(accountId).getNotificationSettings();
  }

  public AccountNotificationSettings update(
      Long accountId, UpdateNotificationSettingsRequest request) {
    Account account = getAccount(accountId);
    AccountNotificationSettings notificationSettings =
        new AccountNotificationSettings(
            request.calendarNotificationsEnabled(),
            request.timedReminderOffset(),
            request.importantReminderOffset(),
            request.allDayReminderTime(),
            request.dailyBriefingEnabled(),
            request.dailyBriefingTime());
    account.changeNotificationSettings(notificationSettings);
    return accountRepository.saveAndFlush(account).getNotificationSettings();
  }

  private Account getAccount(Long accountId) {
    return accountRepository
        .findById(accountId)
        .orElseThrow(() -> new CalioException(ErrorCode.INTERNAL_SERVER_ERROR));
  }
}
