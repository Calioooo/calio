package com.calio.calendar.integration.scheduler;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.GoogleOperationWorker;
import com.calio.calendar.integration.sync.operation.scheduler.GoogleOperationScheduler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleOperationSchedulerTest {

    private final GoogleCalendarIntegrationQueryService integrationQueryService =
            mock(GoogleCalendarIntegrationQueryService.class);
    private final GoogleOperationJobEnqueueService enqueueService =
            mock(GoogleOperationJobEnqueueService.class);
    private final GoogleOperationJobService persistenceService =
            mock(GoogleOperationJobService.class);
    private final GoogleOperationWorker worker = mock(GoogleOperationWorker.class);
    private final GoogleOperationScheduler scheduler = new GoogleOperationScheduler(
            integrationQueryService,
            enqueueService,
            persistenceService,
            worker,
            Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    @DisplayName("주기적 동기화는 연결된 Account를 ID 기준 batch로 끝까지 등록한다")
    void givenConnectedAccounts_whenRecoveringAndEnqueuing_thenProcessesKeysetBatches() {
        // given
        List<Long> firstBatch = LongStream.rangeClosed(1L, 500L).boxed().toList();
        when(persistenceService.findRecoverableAccountIds()).thenReturn(List.of());
        when(integrationQueryService.listConnectedAccountIds(0L, 500)).thenReturn(firstBatch);
        when(integrationQueryService.listConnectedAccountIds(500L, 500))
                .thenReturn(List.of(501L));

        // when
        scheduler.recoverAndEnqueuePeriodicSyncs();

        // then
        verify(enqueueService, times(501)).enqueuePeriodicSync(anyLong());
        verify(enqueueService).enqueuePeriodicSync(1L);
        verify(enqueueService).enqueuePeriodicSync(501L);
        verify(integrationQueryService).listConnectedAccountIds(0L, 500);
        verify(integrationQueryService).listConnectedAccountIds(500L, 500);
    }

    @Test
    @DisplayName("복구 대상(runableAt) Account는 제한된 조회 결과만 worker에 전달한다")
    void givenRecoverableAccounts_whenRecoveringAndEnqueuing_thenWakesReturnedBatch() {
        // given
        when(persistenceService.findRecoverableAccountIds()).thenReturn(List.of(10L, 20L));
        when(integrationQueryService.listConnectedAccountIds(0L, 500)).thenReturn(List.of());

        // when
        scheduler.recoverAndEnqueuePeriodicSyncs();

        // then
        verify(worker).wake(10L);
        verify(worker).wake(20L);
    }
}
