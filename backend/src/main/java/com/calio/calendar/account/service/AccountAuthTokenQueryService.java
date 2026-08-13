package com.calio.calendar.account.service;

import com.calio.calendar.account.domain.AccountAuthToken;
import com.calio.calendar.account.repository.AccountAuthTokenRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountAuthTokenQueryService {

    private final AccountAuthTokenRepository authTokenRepository;

    public AccountAuthTokenQueryService(AccountAuthTokenRepository authTokenRepository) {
        this.authTokenRepository = authTokenRepository;
    }

    public AccountAuthToken getAuthToken(String tokenHash) {
        return authTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CalioException(ErrorCode.AUTH_TOKEN_INVALID));
    }
}
