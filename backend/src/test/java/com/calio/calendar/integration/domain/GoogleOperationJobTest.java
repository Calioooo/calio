package com.calio.calendar.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleOperationJobTest {

    private static final Instant NOW = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    @DisplayName("runnableAt이 현재보다 늦은 Job은 claim할 수 없다")
    void givenDelayedJob_whenCheckingClaimability_thenReturnsFalse() {
        // given
        GoogleOperationJob job = syncJob(NOW.plusSeconds(1));

        // when, then
        assertThat(job.canBeClaimedAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("실행 가능한 Job을 claim하면 상태와 owner token만 변경한다")
    void givenRunnableJob_whenClaimed_thenChangesStateAndOwner() {
        // given
        GoogleOperationJob job = syncJob(NOW);

        // when
        job.claim("worker-token");

        // then
        assertThat(job.getState()).isEqualTo(GoogleOperationJobState.PROCESSING);
        assertThat(job.getOwnerToken()).isEqualTo("worker-token");
        assertThat(job.getRunnableAt()).isEqualTo(NOW);
    }

    private GoogleOperationJob syncJob(Instant runnableAt) {
        return GoogleOperationJob.sync(
                "operation-id",
                1L,
                2L,
                3L,
                GoogleOperationJobTrigger.MANUAL,
                runnableAt
        );
    }
}
