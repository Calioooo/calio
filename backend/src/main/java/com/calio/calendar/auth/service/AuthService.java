package com.calio.calendar.auth.service;

import com.calio.calendar.auth.controller.dto.GuestAuthResponse;
import com.calio.calendar.account.repository.AccountAuthTokenRepository;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountAuthToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final AccountAuthTokenRepository accountAuthTokenRepository;
    private final AccessTokenEncoder accessTokenEncoder;

    public AuthService(
            AccountRepository accountRepository,
            AccountAuthTokenRepository accountAuthTokenRepository,
            AccessTokenEncoder accessTokenEncoder
    ) {
        this.accountRepository = accountRepository;
        this.accountAuthTokenRepository = accountAuthTokenRepository;
        this.accessTokenEncoder = accessTokenEncoder;
    }

    @Transactional
    public GuestAuthResponse issueGuestToken() {
        Account account = accountRepository.save(new Account());
        String rawToken = accessTokenEncoder.generateRawToken();
        String tokenHash = accessTokenEncoder.hash(rawToken);

        accountAuthTokenRepository.save(new AccountAuthToken(account, tokenHash));
        return GuestAuthResponse.bearer(rawToken);
    }
}
