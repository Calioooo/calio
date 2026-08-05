package com.calio.calendar.integration.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.service.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.service.GoogleOperationJobPersistenceService;
import com.calio.calendar.integration.service.GoogleOperationWorker;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class GoogleOperationSchedulerTest {

    private final GoogleCalendarIntegrationRepository integrationRepository =
            mock(GoogleCalendarIntegrationRepository.class);
    private final GoogleOperationJobEnqueueService enqueueService =
            mock(GoogleOperationJobEnqueueService.class);
    private final GoogleOperationJobPersistenceService persistenceService =
            mock(GoogleOperationJobPersistenceService.class);
    private final GoogleOperationWorker worker = mock(GoogleOperationWorker.class);
    private final GoogleOperationScheduler scheduler = new GoogleOperationScheduler(
            integrationRepository,
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
        when(integrationRepository.findConnectedAccountIdsAfter(
                eq(0L),
                any(Pageable.class)
        )).thenReturn(firstBatch);
        when(integrationRepository.findConnectedAccountIdsAfter(
                eq(500L),
                any(Pageable.class)
        )).thenReturn(List.of(501L));

        // when
        scheduler.recoverAndEnqueuePeriodicSyncs();

        // then
        verify(enqueueService, times(501)).enqueuePeriodicSync(anyLong());
        verify(enqueueService).enqueuePeriodicSync(1L);
        verify(enqueueService).enqueuePeriodicSync(501L);
        verify(integrationRepository).findConnectedAccountIdsAfter(
                eq(0L),
                argThat(pageable -> pageable.getPageSize() == 500)
        );
        verify(integrationRepository).findConnectedAccountIdsAfter(
                eq(500L),
                argThat(pageable -> pageable.getPageSize() == 500)
        );
    }

    @Test
    @DisplayName("복구 대상 Account는 제한된 조회 결과만 worker에 전달한다")
    void givenRecoverableAccounts_whenRecoveringAndEnqueuing_thenWakesReturnedBatch() {
        // given
        when(persistenceService.findRecoverableAccountIds()).thenReturn(List.of(10L, 20L));
        when(integrationRepository.findConnectedAccountIdsAfter(
                eq(0L),
                any(Pageable.class)
        )).thenReturn(List.of());

        // when
        scheduler.recoverAndEnqueuePeriodicSyncs();

        // then
        verify(worker).wake(10L);
        verify(worker).wake(20L);
    }
}
