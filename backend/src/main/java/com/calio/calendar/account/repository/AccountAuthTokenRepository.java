package com.calio.calendar.account.repository;

import com.calio.calendar.account.domain.AccountAuthToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountAuthTokenRepository extends JpaRepository<AccountAuthToken, Long> {

    Optional<AccountAuthToken> findByTokenHash(String tokenHash);
}
