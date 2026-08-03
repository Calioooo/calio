package com.calio.calendar.account.repository;

import com.calio.calendar.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.id = :accountId")
    Optional<Account> findByIdForGoogleOperation(@Param("accountId") Long accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE accounts
            SET google_operation_lease_owner = :owner,
                google_operation_lease_expires_at = TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP)
            WHERE id = :accountId
              AND (google_operation_lease_owner IS NULL
                   OR google_operation_lease_expires_at < CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int acquireGoogleOperationLease(@Param("accountId") Long accountId, @Param("owner") String owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE accounts
            SET google_operation_lease_expires_at = TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP)
            WHERE id = :accountId
              AND google_operation_lease_owner = :owner
              AND google_operation_lease_expires_at >= CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int renewGoogleOperationLease(@Param("accountId") Long accountId, @Param("owner") String owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Account account
            set account.googleOperationLeaseOwner = null,
                account.googleOperationLeaseExpiresAt = null
            where account.id = :accountId and account.googleOperationLeaseOwner = :owner
            """)
    int releaseGoogleOperationLease(@Param("accountId") Long accountId, @Param("owner") String owner);
}
