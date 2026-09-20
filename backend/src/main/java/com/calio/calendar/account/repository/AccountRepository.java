package com.calio.calendar.account.repository;

import com.calio.calendar.account.domain.Account;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

  Optional<Account> findByAuthTokenTokenHash(String tokenHash);
}
