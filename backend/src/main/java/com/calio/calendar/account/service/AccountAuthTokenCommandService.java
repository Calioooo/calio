package com.calio.calendar.account.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.domain.AccountAuthToken;
import com.calio.calendar.account.repository.AccountAuthTokenRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AccountAuthTokenCommandService {

    private final AccountAuthTokenRepository authTokenRepository;

    public AccountAuthTokenCommandService(AccountAuthTokenRepository authTokenRepository) {
        this.authTokenRepository = authTokenRepository;
    }

    public AccountAuthToken createAuthToken(Account account, String tokenHash) {
        return authTokenRepository.save(new AccountAuthToken(account, tokenHash));
    }

    public void markAuthTokenUsed(AccountAuthToken authToken, Instant usedAt) {
        authToken.markUsedAt(usedAt);
    }
}
