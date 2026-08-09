package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleOperationJobPersistenceServiceTest {

    @Test
    @DisplayName("복구 대상 Account 조회는 한 번에 500개로 제한한다")
    void givenRecoverableJobs_whenFindingAccountIds_thenRequestsFirstBoundedBatch() {
        // given
        Instant now = Instant.parse("2026-08-04T00:00:00Z");
        GoogleOperationJobRepository jobRepository = mock(GoogleOperationJobRepository.class);
        when(jobRepository.findRecoverableAccountIds(eq(now), argThat(
                pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 500
        ))).thenReturn(List.of(1L, 2L));
        GoogleOperationJobPersistenceService service = new GoogleOperationJobPersistenceService(
                new GoogleOperationJobQueryService(jobRepository),
                new GoogleOperationJobCommandService(jobRepository),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        // when
        List<Long> accountIds = service.findRecoverableAccountIds();

        // then
        assertThat(accountIds).containsExactly(1L, 2L);
        verify(jobRepository).findRecoverableAccountIds(eq(now), argThat(
                pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 500
        ));
    }
}
