package com.calio.calendar.account.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
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
}
