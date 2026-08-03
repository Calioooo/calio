package com.calio.calendar.integration.repository;

import com.calio.calendar.integration.domain.GoogleCalendarOperationJob;
import com.calio.calendar.integration.domain.GoogleCalendarOperationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleCalendarOperationJobRepository
        extends JpaRepository<GoogleCalendarOperationJob, Long> {

    @Query("""
            select distinct job.accountId
            from GoogleCalendarOperationJob job
            where job.integration.state = com.calio.calendar.integration.domain.GoogleCalendarIntegrationState.CONNECTED
              and job.status in :activeStatuses
              and (
                  (job.status = com.calio.calendar.integration.domain.GoogleCalendarOperationStatus.PENDING
                      and job.runnableAt <= :now)
                  or (job.status = com.calio.calendar.integration.domain.GoogleCalendarOperationStatus.PROCESSING
                      and (job.integration.activeSyncRunId is null
                          or job.integration.syncLeaseExpiresAt < :now))
              )
              and not exists (
                  select earlier.id
                  from GoogleCalendarOperationJob earlier
                  where earlier.accountId = job.accountId
                    and earlier.status in :activeStatuses
                    and earlier.sequenceNumber < job.sequenceNumber
              )
            order by job.accountId
            """)
    List<Long> findRunnableAccountIds(
            @Param("now") Instant now,
            @Param("activeStatuses") Collection<GoogleCalendarOperationStatus> activeStatuses,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job
            from GoogleCalendarOperationJob job
            where job.accountId = :accountId
              and job.status in :activeStatuses
            order by job.sequenceNumber
            """)
    List<GoogleCalendarOperationJob> findAccountHeadForUpdate(
            @Param("accountId") Long accountId,
            @Param("activeStatuses") Collection<GoogleCalendarOperationStatus> activeStatuses,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select job
            from GoogleCalendarOperationJob job
            where job.id = :jobId
            """)
    Optional<GoogleCalendarOperationJob> findByIdForUpdate(@Param("jobId") Long jobId);

    boolean existsByAccountIdAndPeriodicDedupKey(Long accountId, String periodicDedupKey);

    @Modifying(flushAutomatically = true)
    @Query("delete from GoogleCalendarOperationJob job where job.integration.id = :integrationId")
    int deleteAllByIntegrationId(@Param("integrationId") Long integrationId);

    @Query("""
            select job.id
            from GoogleCalendarOperationJob job
            where job.terminalAt < :cutoff
            order by job.id
            """)
    List<Long> findTerminalIdsBefore(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("delete from GoogleCalendarOperationJob job where job.id in :jobIds")
    int deleteAllByIds(@Param("jobIds") Collection<Long> jobIds);
}
