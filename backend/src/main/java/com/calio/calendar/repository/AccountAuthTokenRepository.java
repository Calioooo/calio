package com.calio.calendar.repository;

import com.calio.calendar.repository.entity.AccountAuthToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountAuthTokenRepository extends JpaRepository<AccountAuthToken, Long> {

    Optional<AccountAuthToken> findByTokenHash(String tokenHash);
}
