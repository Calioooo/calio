package com.calio.calendar.integration.sync.operation.repository;

import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleOperationJobRepository extends JpaRepository<GoogleOperationJob, Long> {

    @Query(value = """
            SELECT * FROM google_operation_jobs job
            WHERE job.account_id = :accountId
              AND job.job_state IN ('PENDING', 'PROCESSING')
            ORDER BY job.account_sequence
            LIMIT 1
            FOR UPDATE
            """, nativeQuery = true)
    Optional<GoogleOperationJob> findAccountHeadForUpdate(@Param("accountId") Long accountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE google_operation_jobs
            SET job_state = 'PENDING', owner_token = NULL,
                runnable_at = :runnableAt, retry_count = retry_count + 1,
                last_error_reason = :reason, updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId AND job_state = 'PROCESSING'
              AND owner_token = :owner
              AND EXISTS (
                  SELECT 1 FROM google_calendar_integrations integration
                  WHERE integration.id = google_operation_jobs.integration_id
                    AND integration.google_operation_lease_owner = :owner
                    AND integration.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int retry(@Param("jobId") Long jobId, @Param("owner") String owner,
              @Param("runnableAt") Instant runnableAt, @Param("reason") String reason);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE google_operation_jobs
            SET job_state = 'SYNC_ERROR', owner_token = NULL,
                terminal_reason = :reason, terminal_at = CURRENT_TIMESTAMP,
                last_error_reason = :reason, updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId AND job_state = 'PROCESSING'
              AND owner_token = :owner
              AND EXISTS (
                  SELECT 1 FROM google_calendar_integrations integration
                  WHERE integration.id = google_operation_jobs.integration_id
                    AND integration.google_operation_lease_owner = :owner
                    AND integration.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int terminateWithSyncError(@Param("jobId") Long jobId, @Param("owner") String owner,
                               @Param("reason") String reason);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM google_operation_jobs
            WHERE id = :jobId AND job_state = 'PROCESSING'
              AND owner_token = :owner
              AND EXISTS (
                  SELECT 1 FROM google_calendar_integrations integration
                  WHERE integration.id = google_operation_jobs.integration_id
                    AND integration.google_operation_lease_owner = :owner
                    AND integration.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int deleteOwnedSuccessful(@Param("jobId") Long jobId, @Param("owner") String owner);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE google_operation_jobs
            SET conflict_detected = TRUE, updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId AND job_state = 'PROCESSING'
              AND owner_token = :owner
              AND EXISTS (
                  SELECT 1 FROM google_calendar_integrations integration
                  WHERE integration.id = google_operation_jobs.integration_id
                    AND integration.google_operation_lease_owner = :owner
                    AND integration.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int markConflictDetected(@Param("jobId") Long jobId, @Param("owner") String owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE google_operation_jobs
            SET job_state = 'CONFLICTED', owner_token = NULL,
                terminal_reason = 'MAPPING_CONFLICT_DETECTED', terminal_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId AND job_state = 'PROCESSING'
              AND owner_token = :owner AND conflict_detected = TRUE
              AND EXISTS (
                  SELECT 1 FROM google_calendar_integrations integration
                  WHERE integration.id = google_operation_jobs.integration_id
                    AND integration.google_operation_lease_owner = :owner
                    AND integration.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int terminateOwnedConflictDetected(@Param("jobId") Long jobId, @Param("owner") String owner);

    @Query("""
            select distinct job.accountId from GoogleOperationJob job
            where (job.state = com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState.PENDING
                   and job.runnableAt <= :now)
               or job.state = com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState.PROCESSING
            order by job.accountId
            """)
    List<Long> findRecoverableAccountIds(
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            select job.id from GoogleOperationJob job
            where job.state in (com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState.SKIPPED,
                                com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState.CONFLICTED,
                                com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState.SYNC_ERROR)
              and job.terminalAt < :cutoff order by job.id
            """)
    List<Long> findTerminalIdsBefore(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Query("""
            select job.desiredContentHash
            from GoogleOperationJob job
            where job.accountId = :accountId
              and job.integrationId = :integrationId
              and job.kind <> 'SYNC'
              and job.effectiveResourceScope = :scope
              and job.effectiveResourceKey = :key
              and job.state in (
                  com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState.PENDING,
                  com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState.PROCESSING
              )
            order by job.accountSequence
            """)
    List<String> findPendingDesiredContentHashes(
            @Param("accountId") Long accountId,
            @Param("integrationId") Long integrationId,
            @Param("scope") String scope,
            @Param("key") String key
    );

    @Query("""
            select job.desiredContentHash
            from GoogleOperationJob job
            where job.accountId = :accountId
              and job.integrationId = :integrationId
              and job.kind <> 'SYNC'
              and job.state in (
                  com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState.PENDING,
                  com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState.PROCESSING
              )
              and (
                  (job.effectiveResourceScope = 'RECURRENCE_MASTER'
                   and job.effectiveResourceKey = :recurrenceEventKey)
                  or
                  (job.effectiveResourceScope = 'RECURRENCE_OVERRIDE'
                   and job.effectiveResourceKey like concat(:overrideKeyPrefix, '%'))
              )
            order by job.accountSequence
            """)
    List<String> findPendingDesiredContentHashesForRecurrenceAggregate(
            @Param("accountId") Long accountId,
            @Param("integrationId") Long integrationId,
            @Param("recurrenceEventKey") String recurrenceEventKey,
            @Param("overrideKeyPrefix") String overrideKeyPrefix
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GoogleOperationJob job where job.integrationId = :integrationId")
    int deleteByIntegrationId(@Param("integrationId") Long integrationId);
}
