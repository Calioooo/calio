package com.calio.calendar.integration.sync.operation;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.integration.sync.GoogleCalendarSyncService;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionService;
import com.calio.calendar.external.google.GoogleCalendarInvalidGrantException;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScopeType;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.dto.GoogleOperationFailureDecision;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GoogleOperationProcessorTest {

    private GoogleOperationJobService jobPersistenceService;
    private GoogleOperationLeaseService operationLeaseService;
    private GoogleCalendarSyncService syncService;
    private GoogleOperationFailureClassifier failureClassifier;
    private GoogleCalendarConnectionService connectionService;
    private GoogleOperationProcessor processor;

    @BeforeEach
    void setUp() {
        jobPersistenceService = mock(GoogleOperationJobService.class);
        operationLeaseService = mock(GoogleOperationLeaseService.class);
        syncService = mock(GoogleCalendarSyncService.class);
        failureClassifier = mock(GoogleOperationFailureClassifier.class);
        connectionService = mock(GoogleCalendarConnectionService.class);
        processor = new GoogleOperationProcessor(
                jobPersistenceService,
                operationLeaseService,
                syncService,
                failureClassifier,
                connectionService,
                Clock.systemUTC()
        );
    }

    @Test
    @DisplayName("같은 Account의 Sync Job은 account sequence 순서로 하나씩 실행한다")
    void givenTwoJobsForAccount_whenProcess_thenExecutesInFifoOrder() {
        // given
        GoogleOperationJob firstJob = syncJob(1L, 10L);
        GoogleOperationJob secondJob = syncJob(2L, 10L);
        when(operationLeaseService.acquire(eq(10L), anyString())).thenReturn(true);
        when(jobPersistenceService.claimNextJob(eq(10L), anyString()))
                .thenReturn(firstJob, secondJob, null);

        // when
        processor.processAccount(10L);

        // then
        InOrder executionOrder = inOrder(syncService);
        executionOrder.verify(syncService).synchronize(eq(1L), eq(10L), anyString());
        executionOrder.verify(syncService).synchronize(eq(2L), eq(10L), anyString());
        verify(operationLeaseService).release(eq(10L), anyString());
    }

    @Test
    @DisplayName("retry로 판단된 Sync 실패는 재시도 상태로 변경하고 Account 처리를 중단한다")
    void givenRetryableFailure_whenProcess_thenSchedulesRetryAndStopsAccount() {
        // given
        GoogleOperationJob job = syncJob(1L, 10L);
        RuntimeException failure = new RuntimeException("temporary failure");
        when(operationLeaseService.acquire(eq(10L), anyString())).thenReturn(true);
        when(jobPersistenceService.claimNextJob(eq(10L), anyString())).thenReturn(job);
        doThrow(failure).when(syncService).synchronize(eq(1L), eq(10L), anyString());
        when(failureClassifier.classify(failure))
                .thenReturn(GoogleOperationFailureDecision.retry("temporary"));

        // when
        processor.processAccount(10L);

        // then
        verify(jobPersistenceService).retry(eq(job), anyString(), eq("temporary"));
        verify(jobPersistenceService).claimNextJob(eq(10L), anyString());
        verify(jobPersistenceService, never()).terminate(
                eq(1L), eq(10L), anyString(), anyString()
        );
    }

    @Test
    @DisplayName("fail로 판단된 Sync 실패는 종료 상태로 변경하고 다음 Job을 확인한다")
    void givenFailure_whenProcess_thenTerminatesAndContinuesAccount() {
        // given
        GoogleOperationJob job = syncJob(1L, 10L);
        RuntimeException failure = new RuntimeException("permanent failure");
        when(operationLeaseService.acquire(eq(10L), anyString())).thenReturn(true);
        when(jobPersistenceService.claimNextJob(eq(10L), anyString()))
                .thenReturn(job)
                .thenReturn(null);
        doThrow(failure).when(syncService).synchronize(eq(1L), eq(10L), anyString());
        when(failureClassifier.classify(failure))
                .thenReturn(GoogleOperationFailureDecision.fail("permanent"));

        // when
        processor.processAccount(10L);

        // then
        verify(jobPersistenceService).terminate(
                eq(1L), eq(10L), anyString(), eq("permanent")
        );
        verify(jobPersistenceService, times(2)).claimNextJob(eq(10L), anyString());
    }

    @Test
    @DisplayName("재연결이 필요한 provider 오류는 Job을 종료한 뒤 Connection을 SYNC_ERROR로 pause한다")
    void givenReconnectRequiredFailure_whenProcess_thenPausesConnectionAndStopsAccount() {
        GoogleOperationJob job = syncJob(1L, 10L);
        com.calio.calendar.common.error.CalioException failure =
                new com.calio.calendar.common.error.CalioException(
                        com.calio.calendar.common.error.ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED);
        when(operationLeaseService.acquire(eq(10L), anyString())).thenReturn(true);
        when(jobPersistenceService.claimNextJob(eq(10L), anyString())).thenReturn(job);
        doThrow(failure).when(syncService).synchronize(eq(1L), eq(10L), anyString());
        when(failureClassifier.classify(failure))
                .thenReturn(GoogleOperationFailureDecision.fail("GOOGLE_CALENDAR_RECONNECT_REQUIRED"));

        processor.processAccount(10L);

        verify(jobPersistenceService).terminate(eq(1L), eq(10L), anyString(),
                eq("GOOGLE_CALENDAR_RECONNECT_REQUIRED"));
        verify(connectionService).pauseConnectedConnectionForReconnect(eq(10L),
                eq("GOOGLE_CALENDAR_RECONNECT_REQUIRED"), any());
        verify(jobPersistenceService, times(1)).claimNextJob(eq(10L), anyString());
    }

    @Test
    @DisplayName("invalid_grant로 retained disconnect가 완료된 Sync 실패는 삭제된 Job을 다시 종료 처리하지 않는다")
    void givenInvalidGrantAfterRetainedDisconnect_whenProcess_thenStopsWithoutJobTransition() {
        GoogleOperationJob job = syncJob(1L, 10L);
        com.calio.calendar.common.error.CalioException failure =
                new com.calio.calendar.common.error.CalioException(
                        com.calio.calendar.common.error.ErrorCode.GOOGLE_CALENDAR_RECONNECT_REQUIRED,
                        new GoogleCalendarInvalidGrantException(new RuntimeException())
                );
        when(operationLeaseService.acquire(eq(10L), anyString())).thenReturn(true);
        when(jobPersistenceService.claimNextJob(eq(10L), anyString())).thenReturn(job);
        doThrow(failure).when(syncService).synchronize(eq(1L), eq(10L), anyString());

        processor.processAccount(10L);

        verifyNoInteractions(failureClassifier);
        verify(jobPersistenceService, never()).terminate(eq(1L), eq(10L), anyString(), anyString());
        verifyNoInteractions(connectionService);
        verify(jobPersistenceService, times(1)).claimNextJob(eq(10L), anyString());
    }

    @Test
    @DisplayName("skip으로 판단된 Sync 실패는 Job 상태를 변경하지 않고 처리를 중단한다")
    void givenSkippedFailure_whenProcess_thenStopsWithoutTransition() {
        // given
        GoogleOperationJob job = syncJob(1L, 10L);
        RuntimeException failure = new RuntimeException("ownership lost");
        when(operationLeaseService.acquire(eq(10L), anyString())).thenReturn(true);
        when(jobPersistenceService.claimNextJob(eq(10L), anyString())).thenReturn(job);
        doThrow(failure).when(syncService).synchronize(eq(1L), eq(10L), anyString());
        when(failureClassifier.classify(failure))
                .thenReturn(GoogleOperationFailureDecision.skip());

        // when
        processor.processAccount(10L);

        // then
        verify(jobPersistenceService, never()).retry(eq(job), anyString(), anyString());
        verify(jobPersistenceService, never()).terminate(
                eq(1L), eq(10L), anyString(), anyString()
        );
    }

    @Test
    @DisplayName("지원하지 않는 Job kind는 종료 상태로 변경하고 Provider Sync를 호출하지 않는다")
    void givenUnsupportedJobKind_whenProcess_thenTerminatesWithoutSync() {
        // given
        GoogleOperationJob job = job(1L, 10L, "EVENT_UPSERT");
        when(operationLeaseService.acquire(eq(10L), anyString())).thenReturn(true);
        when(jobPersistenceService.claimNextJob(eq(10L), anyString()))
                .thenReturn(job)
                .thenReturn(null);

        // when
        processor.processAccount(10L);

        // then
        verify(jobPersistenceService).terminate(
                eq(1L), eq(10L), anyString(), eq("UNSUPPORTED_JOB_KIND")
        );
        verify(syncService, never()).synchronize(eq(1L), eq(10L), anyString());
        verifyNoInteractions(failureClassifier);
    }

    @Test
    @DisplayName("Account lease를 획득하지 못해도 현재 worker token의 lease 해제를 시도한다")
    void givenLeaseNotAcquired_whenProcess_thenReleasesCurrentWorkerToken() {
        // given
        when(operationLeaseService.acquire(eq(10L), anyString())).thenReturn(false);

        // when
        processor.processAccount(10L);

        // then
        verify(operationLeaseService).release(eq(10L), anyString());
        verify(jobPersistenceService, never()).claimNextJob(eq(10L), anyString());
    }

    private GoogleOperationJob syncJob(Long jobId, Long accountId) {
        return job(jobId, accountId, GoogleOperationJob.SYNC_KIND);
    }

    private GoogleOperationJob job(Long jobId, Long accountId, String kind) {
        GoogleOperationJob job = mock(GoogleOperationJob.class);
        when(job.getId()).thenReturn(jobId);
        when(job.getAccountId()).thenReturn(accountId);
        when(job.getKind()).thenReturn(kind);
        when(job.getConnectionId()).thenReturn(20L);
        when(job.getEffectiveResourceScope()).thenReturn(
                GoogleCalendarEffectiveScopeType.EVENT.getStoredValue());
        when(job.getEffectiveResourceKey()).thenReturn("1");
        return job;
    }
}
