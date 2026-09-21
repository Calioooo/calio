package com.calio.calendar.auth.usecase;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.auth.controller.dto.GuestAuthResponse;
import com.calio.calendar.auth.service.AccessTokenEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueGuestTokenUseCase {

  private final AccountRepository accountRepository;
  private final AccessTokenEncoder accessTokenEncoder;

  public IssueGuestTokenUseCase(
      AccountRepository accountRepository, AccessTokenEncoder accessTokenEncoder) {
    this.accountRepository = accountRepository;
    this.accessTokenEncoder = accessTokenEncoder;
  }

  @Transactional
  public GuestAuthResponse issue() {
    String rawToken = accessTokenEncoder.generateRawToken();
    String tokenHash = accessTokenEncoder.hash(rawToken);
    Account account = new Account();
    account.issueAuthToken(tokenHash);
    accountRepository.save(account);
    return GuestAuthResponse.bearer(rawToken);
  }
}
