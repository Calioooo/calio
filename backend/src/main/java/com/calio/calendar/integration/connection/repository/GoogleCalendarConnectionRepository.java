package com.calio.calendar.integration.connection.repository;

import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarConnectionRepository extends JpaRepository<GoogleCalendarConnection, Long> {

    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            join connection.integration integration
            where integration.accountId = :accountId
              and connection.state = com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState.CONNECTED
            """)
    Optional<GoogleCalendarConnection> findConnectedByAccountId(@Param("accountId") Long accountId);

    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            where connection.id = :connectionId
            """)
    Optional<GoogleCalendarConnection> findByIdWithIntegration(@Param("connectionId") Long connectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            join connection.integration integration
            where integration.accountId = :accountId
            """)
    Optional<GoogleCalendarConnection> findSingleConnectionByAccountIdForUpdate(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            join connection.integration integration
            where integration.accountId = :accountId
              and connection.state = com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState.CONNECTED
            """)
    Optional<GoogleCalendarConnection> findConnectedByAccountIdForUpdate(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "integration")
    @Query("""
            select connection
            from GoogleCalendarConnection connection
            where connection.id = :connectionId
            """)
    Optional<GoogleCalendarConnection> findByIdForUpdate(@Param("connectionId") Long connectionId);

    @Query("""
            select connection.integration.accountId
            from GoogleCalendarConnection connection
            where connection.integration.accountId > :lastAccountId
              and connection.state = com.calio.calendar.integration.connection.domain.GoogleCalendarConnectionState.CONNECTED
            order by connection.integration.accountId
            """)
    List<Long> findConnectedAccountIdsAfter(@Param("lastAccountId") Long lastAccountId, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE google_calendar_connections connection
            SET connection.google_operation_lease_owner = :owner,
                connection.google_operation_lease_expires_at = TIMESTAMPADD(SECOND, :leaseDurationSeconds, CURRENT_TIMESTAMP)
            WHERE connection.connection_state = 'CONNECTED'
              AND (connection.google_operation_lease_owner IS NULL
                   OR connection.google_operation_lease_expires_at < CURRENT_TIMESTAMP)
              AND EXISTS (
                  SELECT 1
                  FROM google_calendar_integrations integration
                  WHERE integration.id = connection.integration_id
                    AND integration.account_id = :accountId
              )
            """, nativeQuery = true)
    int acquireGoogleOperationLease(@Param("accountId") Long accountId, @Param("owner") String owner,
            @Param("leaseDurationSeconds") long leaseDurationSeconds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE google_calendar_connections connection
            SET connection.google_operation_lease_expires_at = TIMESTAMPADD(SECOND, :leaseDurationSeconds, CURRENT_TIMESTAMP)
            WHERE connection.connection_state = 'CONNECTED'
              AND connection.google_operation_lease_owner = :owner
              AND connection.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              AND EXISTS (SELECT 1 FROM google_operation_jobs job
                          WHERE job.id = :jobId AND job.connection_id = connection.id
                            AND job.job_state = 'PROCESSING' AND job.owner_token = :owner)
              AND EXISTS (
                  SELECT 1
                  FROM google_calendar_integrations integration
                  WHERE integration.id = connection.integration_id
                    AND integration.account_id = :accountId
              )
            """, nativeQuery = true)
    int renewOwnedGoogleOperationLease(@Param("jobId") Long jobId, @Param("accountId") Long accountId,
            @Param("owner") String owner, @Param("leaseDurationSeconds") long leaseDurationSeconds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update GoogleCalendarConnection connection
            set connection.googleOperationLeaseOwner = null, connection.googleOperationLeaseExpiresAt = null
            where connection.integration.accountId = :accountId and connection.googleOperationLeaseOwner = :owner
            """)
    int releaseGoogleOperationLease(@Param("accountId") Long accountId, @Param("owner") String owner);
}
