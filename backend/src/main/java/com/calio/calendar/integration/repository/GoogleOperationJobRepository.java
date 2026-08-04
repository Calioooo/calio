package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleOperationJob;
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

    @Query("""
            select distinct job.accountId from GoogleOperationJob job
            where (job.state = com.calio.calendar.integration.domain.GoogleOperationJobState.PENDING
                   and job.runnableAt <= :now)
               or job.state = com.calio.calendar.integration.domain.GoogleOperationJobState.PROCESSING
            order by job.accountId
            """)
    List<Long> findRecoverableAccountIds(
            @Param("now") Instant now,
            Pageable pageable
    );

    @Query("""
            select job.id from GoogleOperationJob job
            where job.state in (com.calio.calendar.integration.domain.GoogleOperationJobState.SKIPPED,
                                com.calio.calendar.integration.domain.GoogleOperationJobState.CONFLICTED,
                                com.calio.calendar.integration.domain.GoogleOperationJobState.SYNC_ERROR)
              and job.terminalAt < :cutoff order by job.id
            """)
    List<Long> findTerminalIdsBefore(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GoogleOperationJob job where job.integrationId = :integrationId")
    int deleteByIntegrationId(@Param("integrationId") Long integrationId);

    @Query("""
            select job.desiredContentHash from GoogleOperationJob job
            where job.accountId = :accountId
              and job.integrationId = :integrationId
              and job.kind <> 'SYNC'
              and job.effectiveResourceScope = :scope
              and job.effectiveResourceKey = :scopeKey
              and job.state in (com.calio.calendar.integration.domain.GoogleOperationJobState.PENDING,
                                com.calio.calendar.integration.domain.GoogleOperationJobState.PROCESSING)
            order by job.accountSequence
            """)
    List<String> findPendingDesiredContentHashes(
            @Param("accountId") Long accountId,
            @Param("integrationId") Long integrationId,
            @Param("scope") String scope,
            @Param("scopeKey") String scopeKey
    );

    @Query(value = """
            SELECT COUNT(*) FROM google_operation_jobs job
            WHERE job.account_id = :accountId
              AND job.integration_id = :integrationId
              AND job.job_kind <> 'SYNC'
              AND job.job_state IN ('PENDING', 'PROCESSING')
              AND (
                  (job.effective_resource_scope = 'RECURRENCE_MASTER'
                   AND job.effective_resource_key = :masterKey)
                  OR
                  (job.effective_resource_scope = 'RECURRENCE_OVERRIDE'
                   AND LEFT(job.effective_resource_key, :childPrefixLength) = :childPrefix)
              )
            """, nativeQuery = true)
    long countPendingRecurrenceAggregateBranches(
            @Param("accountId") Long accountId,
            @Param("integrationId") Long integrationId,
            @Param("masterKey") String masterKey,
            @Param("childPrefix") String childPrefix,
            @Param("childPrefixLength") int childPrefixLength
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE google_operation_jobs SET conflict_detected = TRUE,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId AND account_id = :accountId
              AND job_kind = 'SYNC' AND job_state = 'PROCESSING'
              AND owner_token = :owner
              AND EXISTS (
                  SELECT 1 FROM google_calendar_integrations integration
                  WHERE integration.id = google_operation_jobs.integration_id
                    AND integration.google_operation_lease_owner = :owner
                    AND integration.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int markConflictDetected(@Param("jobId") Long jobId,
                             @Param("accountId") Long accountId,
                             @Param("owner") String owner);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE google_operation_jobs
            SET job_state = 'CONFLICTED', owner_token = NULL,
                terminal_reason = 'MAPPING_CONFLICT_DETECTED',
                last_error_reason = 'MAPPING_CONFLICT_DETECTED',
                terminal_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId AND account_id = :accountId
              AND job_state = 'PROCESSING' AND conflict_detected = TRUE
              AND owner_token = :owner
              AND EXISTS (
                  SELECT 1 FROM google_calendar_integrations integration
                  WHERE integration.id = google_operation_jobs.integration_id
                    AND integration.google_operation_lease_owner = :owner
                    AND integration.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int terminalizeOwnedConflict(@Param("jobId") Long jobId,
                                 @Param("accountId") Long accountId,
                                 @Param("owner") String owner);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE google_operation_jobs
            SET job_state = 'SKIPPED', owner_token = NULL,
                terminal_reason = 'MAPPING_ALREADY_CONFLICTED',
                last_error_reason = 'MAPPING_ALREADY_CONFLICTED',
                terminal_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId AND account_id = :accountId
              AND job_state = 'PROCESSING' AND owner_token = :owner
              AND EXISTS (
                  SELECT 1 FROM google_calendar_integrations integration
                  WHERE integration.id = google_operation_jobs.integration_id
                    AND integration.google_operation_lease_owner = :owner
                    AND integration.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int skipOwnedConflicted(@Param("jobId") Long jobId,
                            @Param("accountId") Long accountId,
                            @Param("owner") String owner);
}
