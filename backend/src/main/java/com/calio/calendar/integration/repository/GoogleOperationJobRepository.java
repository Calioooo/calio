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
            SET job_state = 'PROCESSING', owner_token = :owner,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId AND job_state = 'PENDING'
              AND runnable_at <= CURRENT_TIMESTAMP
              AND EXISTS (
                  SELECT 1 FROM accounts account
                  WHERE account.id = google_operation_jobs.account_id
                    AND account.google_operation_lease_owner = :owner
                    AND account.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int claim(@Param("jobId") Long jobId, @Param("owner") String owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE google_operation_jobs
            SET owner_token = :owner, updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId AND job_state = 'PROCESSING'
              AND EXISTS (
                  SELECT 1 FROM accounts account
                  WHERE account.id = google_operation_jobs.account_id
                    AND account.google_operation_lease_owner = :owner
                    AND account.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int reclaimProcessing(@Param("jobId") Long jobId, @Param("owner") String owner);

    @Query(value = """
            SELECT COUNT(*) FROM google_operation_jobs job
            JOIN accounts account ON account.id = job.account_id
            WHERE job.id = :jobId AND job.job_state = 'PROCESSING'
              AND job.owner_token = :owner
              AND account.google_operation_lease_owner = :owner
              AND account.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int countActiveOwnership(@Param("jobId") Long jobId, @Param("owner") String owner);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE google_operation_jobs
            SET job_state = 'PENDING', owner_token = NULL,
                runnable_at = :runnableAt, retry_count = retry_count + 1,
                last_error_reason = :reason, updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId AND job_state = 'PROCESSING'
              AND owner_token = :owner
              AND EXISTS (
                  SELECT 1 FROM accounts account
                  WHERE account.id = google_operation_jobs.account_id
                    AND account.google_operation_lease_owner = :owner
                    AND account.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
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
                  SELECT 1 FROM accounts account
                  WHERE account.id = google_operation_jobs.account_id
                    AND account.google_operation_lease_owner = :owner
                    AND account.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
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
                  SELECT 1 FROM accounts account
                  WHERE account.id = google_operation_jobs.account_id
                    AND account.google_operation_lease_owner = :owner
                    AND account.google_operation_lease_expires_at >= CURRENT_TIMESTAMP
              )
            """, nativeQuery = true)
    int deleteOwnedSuccessful(@Param("jobId") Long jobId, @Param("owner") String owner);

    @Query("""
            select distinct job.accountId from GoogleOperationJob job
            where (job.state = com.calio.calendar.integration.domain.GoogleOperationJobState.PENDING
                   and job.runnableAt <= :now)
               or job.state = com.calio.calendar.integration.domain.GoogleOperationJobState.PROCESSING
            """)
    List<Long> findRecoverableAccountIds(@Param("now") Instant now);

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
}
