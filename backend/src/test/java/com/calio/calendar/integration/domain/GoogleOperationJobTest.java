package com.calio.calendar.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarSyncJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJobKind;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobState;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobTrigger;
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
    @DisplayName("실행 가능한 Job을 claim하면 상태와 owner token이 변경된다")
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
    @DisplayName("Sync Job을 CANONICAL_MUTATION trigger로 생성하면 예외를 반환한다")
    void givenCanonicalMutationTrigger_whenCreatingSyncJob_thenRejectsTrigger() {
        assertThatThrownBy(() -> GoogleCalendarSyncJob.create(
                "operation-id",
                1L,
                2L,
                3L,
                GoogleOperationJobTrigger.CANONICAL_MUTATION,
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Event Job은 mutation kind를 요구한다")
    void givenMissingKind_whenCreatingEventJob_thenRejectsKind() {
        assertThatThrownBy(() -> GoogleCalendarEventJob.create(
                "operation-id",
                1L,
                2L,
                3L,
                null,
                4L,
                null,
                "{}",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Event Job은 event ID를 요구한다")
    void givenMissingEventId_whenCreatingEventJob_thenRejectsTarget() {
        assertThatThrownBy(() -> GoogleCalendarEventJob.create(
                "operation-id",
                1L,
                2L,
                3L,
                GoogleCalendarEventJobKind.UPDATE,
                null,
                null,
                "{}",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Event Job은 immutable target payload를 요구한다")
    void givenMissingPayload_whenCreatingOutboundJob_thenRejectsPayload() {
        assertThatThrownBy(() -> GoogleCalendarEventJob.create(
                "operation-id",
                1L,
                2L,
                3L,
                GoogleCalendarEventJobKind.UPDATE,
                4L,
                null,
                "",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CREATE Job은 재시도에 사용할 provider identity를 요구한다")
    void givenMissingProviderIdentity_whenCreatingCreateJob_thenRejectsJob() {
        assertThatThrownBy(() -> GoogleCalendarEventJob.create(
                "operation-id",
                1L,
                2L,
                3L,
                GoogleCalendarEventJobKind.CREATE,
                4L,
                null,
                "{}",
                NOW
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private GoogleOperationJob syncJob(Instant runnableAt) {
        return GoogleCalendarSyncJob.create(
                "operation-id",
                1L,
                2L,
                3L,
                GoogleOperationJobTrigger.MANUAL,
                runnableAt
        );
    }
}
