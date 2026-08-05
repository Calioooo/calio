package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.integration.domain.GoogleOperationJob;
import com.calio.calendar.integration.service.GoogleOperationJobPersistenceService.GoogleOperationOwnershipLostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.verification.VerificationWithTimeout;
import org.springframework.dao.TransientDataAccessResourceException;

class GoogleOperationWorkerTest {

    private GoogleOperationJobPersistenceService persistenceService;
    private GoogleCalendarSyncService syncService;
    private GoogleOperationWorker worker;

    @BeforeEach
    void setUp() {
        persistenceService = mock(GoogleOperationJobPersistenceService.class);
        syncService = mock(GoogleCalendarSyncService.class);
        worker = new GoogleOperationWorker(persistenceService, syncService);
    }

    @AfterEach
    void tearDown() {
        worker.shutdown();
    }

    @Test
    @DisplayName("같은 Account의 Sync Job은 account sequence 순서로 하나씩 실행한다")
    void givenTwoJobsForAccount_whenWoken_thenExecutesInFifoOrder() {
        // given
        GoogleOperationJob firstJob = syncJob(1L, 10L);
        GoogleOperationJob secondJob = syncJob(2L, 10L);
        when(persistenceService.acquireLease(eq(10L), anyString())).thenReturn(true);
        when(persistenceService.claimHead(eq(10L), anyString()))
                .thenReturn(firstJob, secondJob, null);

        // when
        worker.wake(10L);

        // then
        verify(persistenceService, timeout()).releaseLease(eq(10L), anyString());
        InOrder executionOrder = inOrder(syncService);
        executionOrder.verify(syncService).executeOwned(eq(1L), eq(10L), anyString());
        executionOrder.verify(syncService).executeOwned(eq(2L), eq(10L), anyString());
    }

    @Test
    @DisplayName("일시적인 Sync 실패는 retry로 전환하고 같은 Account 처리를 중단한다")
    void givenTransientFailure_whenExecutingJob_thenSchedulesRetryAndStopsAccount() {
        // given
        GoogleOperationJob job = syncJob(1L, 10L);
        when(persistenceService.acquireLease(eq(10L), anyString())).thenReturn(true);
        when(persistenceService.claimHead(eq(10L), anyString())).thenReturn(job);
        doThrow(new TransientDataAccessResourceException("temporary failure"))
                .when(syncService)
                .executeOwned(eq(1L), eq(10L), anyString());

        // when
        worker.wake(10L);

        // then
        verify(persistenceService, timeout()).retry(
                eq(job),
                anyString(),
                eq(TransientDataAccessResourceException.class.getSimpleName())
        );
        verify(persistenceService, timeout()).releaseLease(eq(10L), anyString());
        verify(persistenceService, never()).terminate(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("영구적인 Sync 실패는 terminal로 전환하고 다음 Job 처리를 계속한다")
    void givenPermanentFailure_whenExecutingJob_thenTerminatesAndContinuesAccount() {
        // given
        GoogleOperationJob job = syncJob(1L, 10L);
        when(persistenceService.acquireLease(eq(10L), anyString())).thenReturn(true);
        when(persistenceService.claimHead(eq(10L), anyString()))
                .thenReturn(job)
                .thenReturn(null);
        doThrow(new CalioException(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED))
                .when(syncService)
                .executeOwned(eq(1L), eq(10L), anyString());

        // when
        worker.wake(10L);

        // then
        verify(persistenceService, timeout()).terminate(
                eq(1L),
                eq(10L),
                anyString(),
                eq(ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED.name())
        );
        verify(persistenceService, timeout().times(2)).claimHead(eq(10L), anyString());
        verify(persistenceService, timeout()).releaseLease(eq(10L), anyString());
    }

    @Test
    @DisplayName("operation ownership을 잃으면 Job 상태를 변경하지 않고 Account 처리를 중단한다")
    void givenOwnershipLost_whenExecutingJob_thenAbandonsWithoutTransition() {
        // given
        GoogleOperationJob job = syncJob(1L, 10L);
        when(persistenceService.acquireLease(eq(10L), anyString())).thenReturn(true);
        when(persistenceService.claimHead(eq(10L), anyString())).thenReturn(job);
        doThrow(new GoogleOperationOwnershipLostException())
                .when(syncService)
                .executeOwned(eq(1L), eq(10L), anyString());

        // when
        worker.wake(10L);

        // then
        verify(persistenceService, timeout()).releaseLease(eq(10L), anyString());
        verify(persistenceService, never()).retry(any(), anyString(), anyString());
        verify(persistenceService, never()).terminate(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("지원하지 않는 Job kind는 terminal 처리하고 provider Sync를 호출하지 않는다")
    void givenUnsupportedJobKind_whenWoken_thenTerminatesWithoutSync() {
        // given
        GoogleOperationJob job = job(1L, 10L, "EVENT_UPSERT");
        when(persistenceService.acquireLease(eq(10L), anyString())).thenReturn(true);
        when(persistenceService.claimHead(eq(10L), anyString()))
                .thenReturn(job)
                .thenReturn(null);

        // when
        worker.wake(10L);

        // then
        verify(persistenceService, timeout()).terminate(
                eq(1L),
                eq(10L),
                anyString(),
                eq("UNSUPPORTED_JOB_KIND")
        );
        verify(syncService, never()).executeOwned(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("동기화 대상이 이미 충돌 상태면 Google 호출 전에 쓰기 작업을 건너뛴다")
    void givenConflictedSyncTarget_whenWorkerRuns_thenSkipsBeforeGoogleCall() {
        // given
        GoogleOperationJob job = job(1L, 10L, "EVENT_UPSERT");
        when(persistenceService.acquireLease(eq(10L), anyString())).thenReturn(true);
        when(persistenceService.claimHead(eq(10L), anyString()))
                .thenReturn(job)
                .thenReturn(null);
        when(persistenceService.skipIfTargetConflicted(eq(job), anyString())).thenReturn(true);

        // when
        worker.wake(10L);

        // then
        verify(persistenceService, timeout()).skipIfTargetConflicted(eq(job), anyString());
        verify(persistenceService, never()).terminate(
                anyLong(), anyLong(), anyString(), anyString());
        verify(syncService, never()).executeOwned(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("처리 중인 Account의 중복 wake는 별도 worker를 시작하지 않는다")
    void givenActiveAccount_whenWokenAgain_thenKeepsSingleWorker() throws Exception {
        // given
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch completeExecution = new CountDownLatch(1);
        GoogleOperationJob job = syncJob(1L, 10L);
        when(persistenceService.acquireLease(eq(10L), anyString())).thenReturn(true);
        when(persistenceService.claimHead(eq(10L), anyString()))
                .thenReturn(job)
                .thenReturn(null);
        doAnswer(invocation -> {
            executionStarted.countDown();
            completeExecution.await(2, TimeUnit.SECONDS);
            return GoogleCalendarSyncMode.FULL;
        }).when(syncService).executeOwned(eq(1L), eq(10L), anyString());

        // when
        worker.wake(10L);
        assertThat(executionStarted.await(2, TimeUnit.SECONDS)).isTrue();
        worker.wake(10L);

        // then
        verify(persistenceService).acquireLease(eq(10L), anyString());
        completeExecution.countDown();
        verify(persistenceService, timeout()).releaseLease(eq(10L), anyString());
        verify(persistenceService).acquireLease(eq(10L), anyString());
    }

    @Test
    @DisplayName("서로 다른 Account 작업은 최대 4개까지만 동시에 실행한다")
    void givenFiveAccounts_whenWoken_thenRunsAtMostFourConcurrently() throws Exception {
        // given
        CountDownLatch firstFourStarted = new CountDownLatch(4);
        CountDownLatch completeExecutions = new CountDownLatch(1);
        AtomicInteger activeExecutions = new AtomicInteger();
        AtomicInteger maximumExecutions = new AtomicInteger();
        Map<Long, AtomicInteger> claimsByAccount = new ConcurrentHashMap<>();
        when(persistenceService.acquireLease(anyLong(), anyString())).thenReturn(true);
        when(persistenceService.claimHead(anyLong(), anyString())).thenAnswer(invocation -> {
            Long accountId = invocation.getArgument(0);
            int claimCount = claimsByAccount
                    .computeIfAbsent(accountId, ignored -> new AtomicInteger())
                    .getAndIncrement();
            return claimCount == 0 ? syncJob(accountId, accountId) : null;
        });
        doAnswer(invocation -> {
            int currentExecutions = activeExecutions.incrementAndGet();
            maximumExecutions.accumulateAndGet(currentExecutions, Math::max);
            firstFourStarted.countDown();
            try {
                completeExecutions.await(3, TimeUnit.SECONDS);
            } finally {
                activeExecutions.decrementAndGet();
            }
            return GoogleCalendarSyncMode.FULL;
        }).when(syncService).executeOwned(anyLong(), anyLong(), anyString());

        // when
        for (long accountId = 1L; accountId <= 5L; accountId++) {
            worker.wake(accountId);
        }
        assertThat(firstFourStarted.await(2, TimeUnit.SECONDS)).isTrue();

        // then
        assertThat(maximumExecutions).hasValue(4);
        verify(syncService, times(4)).executeOwned(anyLong(), anyLong(), anyString());
        completeExecutions.countDown();
        verify(syncService, timeout().times(5)).executeOwned(anyLong(), anyLong(), anyString());
        verify(persistenceService, timeout().times(5)).releaseLease(anyLong(), anyString());
    }

    private GoogleOperationJob syncJob(Long jobId, Long accountId) {
        return job(jobId, accountId, GoogleOperationJob.SYNC_KIND);
    }

    private GoogleOperationJob job(Long jobId, Long accountId, String kind) {
        GoogleOperationJob job = mock(GoogleOperationJob.class);
        when(job.getId()).thenReturn(jobId);
        when(job.getAccountId()).thenReturn(accountId);
        when(job.getKind()).thenReturn(kind);
        return job;
    }

    private VerificationWithTimeout timeout() {
        return org.mockito.Mockito.timeout(2_000L);
    }
}
