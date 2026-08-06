package com.calio.calendar.auth.service;

import com.calio.calendar.auth.controller.dto.GuestAuthResponse;
import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountCommandService;
import com.calio.calendar.account.service.AccountAuthTokenCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AccountCommandService accountCommandService;
    private final AccountAuthTokenCommandService authTokenCommandService;
    private final AccessTokenEncoder accessTokenEncoder;

    public AuthService(
            AccountCommandService accountCommandService,
            AccountAuthTokenCommandService authTokenCommandService,
            AccessTokenEncoder accessTokenEncoder
    ) {
        this.accountCommandService = accountCommandService;
        this.authTokenCommandService = authTokenCommandService;
        this.accessTokenEncoder = accessTokenEncoder;
    }

    @Transactional
    public GuestAuthResponse issueGuestToken() {
        Account account = accountCommandService.createAccount();
        String rawToken = accessTokenEncoder.generateRawToken();
        String tokenHash = accessTokenEncoder.hash(rawToken);

        authTokenCommandService.createAuthToken(account, tokenHash);
        return GuestAuthResponse.bearer(rawToken);
    }
}
