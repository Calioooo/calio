package com.calio.calendar.account.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class AccountCommandService {

    private final AccountRepository accountRepository;
    public AccountCommandService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account createAccount() {
        return accountRepository.save(new Account());
    }

    public Account lockAccount(Long accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
