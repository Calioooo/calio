package com.calio.calendar.security.usecase;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.auth.service.AccessTokenEncoder;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.security.AuthenticatedAccount;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticateAccountTokenUseCase {

  private final AccountRepository accountRepository;
  private final AccessTokenEncoder accessTokenEncoder;
  private final Clock clock;

  public AuthenticateAccountTokenUseCase(
      AccountRepository accountRepository, AccessTokenEncoder accessTokenEncoder, Clock clock) {
    this.accountRepository = accountRepository;
    this.accessTokenEncoder = accessTokenEncoder;
    this.clock = clock;
  }

  @Transactional
  public AuthenticatedAccount authenticate(String rawToken) {
    String tokenHash = accessTokenEncoder.hash(rawToken);
    Account account =
        accountRepository
            .findByAuthTokenTokenHash(tokenHash)
            .orElseThrow(() -> new CalioException(ErrorCode.AUTH_TOKEN_INVALID));
    account.authenticateAuthToken(clock.instant());
    return new AuthenticatedAccount(account.getId());
  }
}
