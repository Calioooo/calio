package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
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

    @Query("select integration.accountId from GoogleCalendarIntegration integration")
    List<Long> findAllConnectedAccountIds();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE google_calendar_integrations
            SET active_sync_run_id = :runId,
                sync_lease_expires_at = TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP)
            WHERE account_id = :accountId
              AND (
                  active_sync_run_id IS NULL
                  OR sync_lease_expires_at < CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int acquireSyncLease(@Param("accountId") Long accountId, @Param("runId") String runId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE google_calendar_integrations
            SET sync_lease_expires_at = TIMESTAMPADD(MINUTE, 5, CURRENT_TIMESTAMP)
            WHERE id = :integrationId
              AND active_sync_run_id = :runId
              AND sync_lease_expires_at >= CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int extendSyncLease(
            @Param("integrationId") Long integrationId,
            @Param("runId") String runId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("""
            update GoogleCalendarIntegration integration
            set integration.activeSyncRunId = null,
                integration.syncLeaseExpiresAt = null
            where integration.id = :integrationId
              and integration.activeSyncRunId = :runId
            """)
    int releaseSyncLease(
            @Param("integrationId") Long integrationId,
            @Param("runId") String runId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GoogleCalendarIntegration integration
            set integration.nextSyncToken = :nextSyncToken,
                integration.activeSyncRunId = null,
                integration.syncLeaseExpiresAt = null
            where integration.id = :integrationId
              and integration.activeSyncRunId = :runId
              and integration.syncLeaseExpiresAt >= CURRENT_TIMESTAMP
            """)
    int finalizeSync(
            @Param("integrationId") Long integrationId,
            @Param("runId") String runId,
            @Param("nextSyncToken") String nextSyncToken
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GoogleCalendarIntegration integration
            set integration.nextSyncToken = null
            where integration.id = :integrationId
              and integration.activeSyncRunId = :runId
              and integration.syncLeaseExpiresAt >= CURRENT_TIMESTAMP
            """)
    int clearCursorUnderLease(
            @Param("integrationId") Long integrationId,
            @Param("runId") String runId
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
