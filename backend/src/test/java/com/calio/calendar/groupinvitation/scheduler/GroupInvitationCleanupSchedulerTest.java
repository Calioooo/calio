package com.calio.calendar.groupinvitation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.service.GroupInvitationCommandService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupInvitationCleanupSchedulerTest {

    @Mock
    private GroupInvitationCommandService commandService;

    @Test
    @DisplayName("cleanup은 retention cutoff로 batch가 가득 찬 동안 독립 batch 삭제를 반복한다")
    void repeatsFullBatchesUntilPartialBatch() {
        // given
        Instant now = Instant.parse("2026-07-28T08:00:00Z");
        Instant cutoff = Instant.parse("2026-07-27T08:00:00Z");
        GroupInvitationProperties properties = properties(2, 10);
        GroupInvitationCleanupScheduler scheduler = new GroupInvitationCleanupScheduler(
                commandService,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(commandService.deleteExpiredBatch(cutoff)).thenReturn(2, 2, 1);

        // when
        scheduler.deleteRetainedExpiredInvitations();

        // then
        verify(commandService, times(3)).deleteExpiredBatch(cutoff);
    }

    @Test
    @DisplayName("cleanup은 backlog가 있어도 configured run batch 상한에서 멈춘다")
    void stopsAtConfiguredBatchLimit() {
        // given
        Instant now = Instant.parse("2026-07-28T08:00:00Z");
        Instant cutoff = Instant.parse("2026-07-27T08:00:00Z");
        GroupInvitationProperties properties = properties(2, 3);
        GroupInvitationCleanupScheduler scheduler = new GroupInvitationCleanupScheduler(
                commandService,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(commandService.deleteExpiredBatch(cutoff)).thenReturn(2);

        // when
        scheduler.deleteRetainedExpiredInvitations();

        // then
        verify(commandService, times(3)).deleteExpiredBatch(cutoff);
    }

    @Test
    @DisplayName("cleanup 실패는 원인을 보존한 전용 CalioException으로 변환한다")
    void mapsCleanupFailureToCalioException() {
        // given
        Instant now = Instant.parse("2026-07-28T08:00:00Z");
        Instant cutoff = Instant.parse("2026-07-27T08:00:00Z");
        IllegalStateException cause = new IllegalStateException("cleanup failed");
        GroupInvitationCleanupScheduler scheduler = new GroupInvitationCleanupScheduler(
                commandService,
                properties(2, 3),
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(commandService.deleteExpiredBatch(cutoff)).thenThrow(cause);

        // when, then
        assertThatThrownBy(scheduler::deleteRetainedExpiredInvitations)
                .isInstanceOfSatisfying(CalioException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.GROUP_INVITATION_CLEANUP_FAILED);
                    assertThat(exception.getCause()).isSameAs(cause);
                });
    }

    private GroupInvitationProperties properties(int batchSize, int maxBatches) {
        GroupInvitationProperties properties = new GroupInvitationProperties();
        properties.setExpiredRetention(Duration.ofHours(24));
        properties.setCleanupBatchSize(batchSize);
        properties.setCleanupMaxBatchesPerRun(maxBatches);
        return properties;
    }
}
