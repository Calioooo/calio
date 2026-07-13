package com.calio.calendar.security;

import com.calio.calendar.auth.service.AccessTokenEncoder;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.account.repository.AccountAuthTokenRepository;
import com.calio.calendar.account.domain.AccountAuthToken;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountTokenAuthenticationService {

    private final AccountAuthTokenRepository accountAuthTokenRepository;
    private final AccessTokenEncoder accessTokenEncoder;
    private final Clock clock;

    public AccountTokenAuthenticationService(
            AccountAuthTokenRepository accountAuthTokenRepository,
            AccessTokenEncoder accessTokenEncoder,
            Clock clock
    ) {
        this.accountAuthTokenRepository = accountAuthTokenRepository;
        this.accessTokenEncoder = accessTokenEncoder;
        this.clock = clock;
    }

    @Transactional
    public AuthenticatedAccount authenticate(String rawToken) {
        String tokenHash = accessTokenEncoder.hash(rawToken);
        AccountAuthToken authToken = accountAuthTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CalioException(ErrorCode.AUTH_TOKEN_INVALID));

        if (authToken.getRevokedAt() != null) {
            throw new CalioException(ErrorCode.AUTH_TOKEN_REVOKED);
        }

        authToken.markUsedAt(clock.instant());
        return new AuthenticatedAccount(authToken.getAccountId());
    }
}
