package com.calio.calendar.integration.connection.repository;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GoogleCalendarIntegrationRepository extends JpaRepository<GoogleCalendarIntegration, Long> {

    Optional<GoogleCalendarIntegration> findByAccountId(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select integration
            from GoogleCalendarIntegration integration
            where integration.accountId = :accountId
            """)
    Optional<GoogleCalendarIntegration> findByAccountIdForUpdate(@Param("accountId") Long accountId);

    boolean existsByAccountId(Long accountId);

    @Query("""
            select integration.accountId
            from GoogleCalendarIntegration integration
            where integration.accountId > :lastAccountId
            order by integration.accountId
            """)
    List<Long> findConnectedAccountIdsAfter(
            @Param("lastAccountId") Long lastAccountId,
            Pageable pageable
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE google_calendar_integrations
            SET google_operation_lease_owner = :owner,
                google_operation_lease_expires_at = TIMESTAMPADD(
                    SECOND,
                    :leaseDurationSeconds,
                    CURRENT_TIMESTAMP
                )
            WHERE account_id = :accountId
              AND (google_operation_lease_owner IS NULL
                   OR google_operation_lease_expires_at < CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int acquireGoogleOperationLease(
            @Param("accountId") Long accountId,
            @Param("owner") String owner,
            @Param("leaseDurationSeconds") long leaseDurationSeconds
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE google_calendar_integrations
            SET google_operation_lease_expires_at = TIMESTAMPADD(
                SECOND,
                :leaseDurationSeconds,
                CURRENT_TIMESTAMP
            )
            WHERE account_id = :accountId
              AND google_operation_lease_owner = :owner
              AND google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              AND EXISTS (
                  SELECT 1 FROM google_operation_jobs job
                  WHERE job.id = :jobId
                    AND job.integration_id = google_calendar_integrations.id
                    AND job.job_state = 'PROCESSING'
                    AND job.owner_token = :owner
              )
            """, nativeQuery = true)
    int renewOwnedGoogleOperationLease(
            @Param("jobId") Long jobId,
            @Param("accountId") Long accountId,
            @Param("owner") String owner,
            @Param("leaseDurationSeconds") long leaseDurationSeconds
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GoogleCalendarIntegration integration
            set integration.googleOperationLeaseOwner = null,
                integration.googleOperationLeaseExpiresAt = null
            where integration.accountId = :accountId
              and integration.googleOperationLeaseOwner = :owner
            """)
    int releaseGoogleOperationLease(@Param("accountId") Long accountId, @Param("owner") String owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GoogleCalendarIntegration integration
            set integration.nextSyncToken = :nextSyncToken
            where integration.id = :integrationId
            """)
    int updateNextSyncToken(
            @Param("integrationId") Long integrationId,
            @Param("nextSyncToken") String nextSyncToken
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("""
            update GoogleCalendarIntegration integration
            set integration.encryptedAccessToken = :encryptedAccessToken,
                integration.accessTokenExpiresAt = :accessTokenExpiresAt
            where integration.id = :integrationId
              and integration.encryptedRefreshToken = :expectedEncryptedRefreshToken
            """)
    int updateAccessToken(
            @Param("integrationId") Long integrationId,
            @Param("expectedEncryptedRefreshToken") String expectedEncryptedRefreshToken,
            @Param("encryptedAccessToken") String encryptedAccessToken,
            @Param("accessTokenExpiresAt") java.time.Instant accessTokenExpiresAt
    );
}
