package com.calio.calendar.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("Sync Job은 CANONICAL_MUTATION trigger를 허용하지 않는다")
    void givenCanonicalMutationTrigger_whenCreatingSyncJob_thenRejectsTrigger() {
        assertThatThrownBy(() -> GoogleOperationJob.sync(
                "operation-id",
                1L,
                2L,
                3L,
                GoogleOperationJobTrigger.CANONICAL_MUTATION,
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Outbound Job은 SYNC kind를 허용하지 않는다")
    void givenSyncKind_whenCreatingOutboundJob_thenRejectsKind() {
        assertThatThrownBy(() -> GoogleOperationJob.outbound(
                "operation-id",
                1L,
                2L,
                3L,
                GoogleOperationJob.SYNC_KIND,
                GoogleCalendarSyncTarget.event(1L),
                null,
                "{}",
                hash("desired"),
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Outbound Job은 실행 대상을 요구한다")
    void givenMissingResourceScope_whenCreatingOutboundJob_thenRejectsTarget() {
        assertThatThrownBy(() -> GoogleOperationJob.outbound(
                "operation-id",
                1L,
                2L,
                3L,
                "EVENT_UPSERT",
                null,
                null,
                "{}",
                hash("desired"),
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Outbound Job은 immutable desired payload를 요구한다")
    void givenMissingPayload_whenCreatingOutboundJob_thenRejectsPayload() {
        assertThatThrownBy(() -> GoogleOperationJob.outbound(
                "operation-id",
                1L,
                2L,
                3L,
                "EVENT_UPSERT",
                GoogleCalendarSyncTarget.event(1L),
                null,
                "",
                hash("desired"),
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private String hash(String value) {
        return GoogleContentHash.digest("TEST", value);
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
