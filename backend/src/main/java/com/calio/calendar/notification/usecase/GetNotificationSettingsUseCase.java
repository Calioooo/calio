package com.calio.calendar.notification.usecase;

import com.calio.calendar.account.domain.AccountNotificationSettings;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetNotificationSettingsUseCase {

  private final AccountRepository accountRepository;

  public GetNotificationSettingsUseCase(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  public AccountNotificationSettings execute(Long accountId) {
    return accountRepository
        .findById(accountId)
        .orElseThrow(() -> new CalioException(ErrorCode.INTERNAL_SERVER_ERROR))
        .getNotificationSettings();
  }
}
