package com.calio.calendar.auth;

import com.calio.calendar.auth.dto.AnonymousAuthResponse;
import com.calio.calendar.repository.AccountAuthTokenRepository;
import com.calio.calendar.repository.AccountRepository;
import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.AccountAuthToken;
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
    public AnonymousAuthResponse issueAnonymousToken() {
        Account account = accountRepository.save(new Account());
        String rawToken = accessTokenEncoder.generateRawToken();
        String tokenHash = accessTokenEncoder.hash(rawToken);

        accountAuthTokenRepository.save(new AccountAuthToken(account, tokenHash));
        return AnonymousAuthResponse.bearer(rawToken);
    }
}
