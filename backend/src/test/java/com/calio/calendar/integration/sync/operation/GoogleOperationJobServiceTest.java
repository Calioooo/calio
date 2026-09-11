package com.calio.calendar.integration.sync.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.integration.sync.operation.repository.GoogleOperationJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GoogleOperationJobServiceTest {

    @Test
    @DisplayName("conflict 완료는 conflict 기록 후 Job을 terminal 상태로 전환한다")
    void completeWithConflictRecordsAndCompletesJob() {
        // given
        GoogleOperationJobRepository jobRepository = mock(GoogleOperationJobRepository.class);
        when(jobRepository.markConflictDetected(10L, "worker")).thenReturn(1);
        when(jobRepository.terminateOwnedConflictDetected(10L, "worker")).thenReturn(1);
        GoogleOperationJobService service = service(jobRepository, Instant.EPOCH);

        // when
        service.completeWithConflict(10L, 20L, "worker");

        // then
        InOrder transitions = inOrder(jobRepository);
        transitions.verify(jobRepository).markConflictDetected(10L, "worker");
        transitions.verify(jobRepository).terminateOwnedConflictDetected(10L, "worker");
    }

    @Test
    @DisplayName("복구 대상 Account 조회는 한 번에 500개로 제한한다")
    void givenRecoverableJobs_whenFindingAccountIds_thenRequestsFirstBoundedBatch() {
        // given
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        GoogleOperationJobRepository jobRepository = mock(GoogleOperationJobRepository.class);
        when(jobRepository.findRecoverableAccountIds(eq(now), argThat(
                pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 500
        ))).thenReturn(List.of(1L, 2L));
        GoogleOperationJobService service = service(jobRepository, now);

        // when
        List<Long> accountIds = service.findRecoverableAccountIds();

        // then
        assertThat(accountIds).containsExactly(1L, 2L);
        verify(jobRepository).findRecoverableAccountIds(eq(now), argThat(
                pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 500
        ));
    }

    private GoogleOperationJobService service(
            GoogleOperationJobRepository jobRepository,
            Instant now
    ) {
        return new GoogleOperationJobService(
                new GoogleOperationJobQueryService(jobRepository),
                new GoogleOperationJobCommandService(jobRepository),
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }
}
