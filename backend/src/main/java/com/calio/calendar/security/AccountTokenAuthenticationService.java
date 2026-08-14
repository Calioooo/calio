package com.calio.calendar.security;

import com.calio.calendar.auth.service.AccessTokenEncoder;
import com.calio.calendar.account.service.AccountAuthTokenCommandService;
import com.calio.calendar.account.service.AccountAuthTokenQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.account.domain.AccountAuthToken;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountTokenAuthenticationService {

    private final AccountAuthTokenQueryService authTokenQueryService;
    private final AccountAuthTokenCommandService authTokenCommandService;
    private final AccessTokenEncoder accessTokenEncoder;
    private final Clock clock;

    public AccountTokenAuthenticationService(
            AccountAuthTokenQueryService authTokenQueryService,
            AccountAuthTokenCommandService authTokenCommandService,
            AccessTokenEncoder accessTokenEncoder,
            Clock clock
    ) {
        this.authTokenQueryService = authTokenQueryService;
        this.authTokenCommandService = authTokenCommandService;
        this.accessTokenEncoder = accessTokenEncoder;
        this.clock = clock;
    }

    @Transactional
    public AuthenticatedAccount authenticate(String rawToken) {
        String tokenHash = accessTokenEncoder.hash(rawToken);
        AccountAuthToken authToken = authTokenQueryService.getAuthToken(tokenHash);

        if (authToken.getRevokedAt() != null) {
            throw new CalioException(ErrorCode.AUTH_TOKEN_REVOKED);
        }

        authTokenCommandService.markAuthTokenUsed(authToken, clock.instant());
        return new AuthenticatedAccount(authToken.getAccountId());
    }
}
