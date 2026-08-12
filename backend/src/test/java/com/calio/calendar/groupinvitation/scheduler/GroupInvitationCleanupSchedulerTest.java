package com.calio.calendar.groupinvitation.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.service.GroupInvitationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class GroupInvitationCleanupSchedulerTest {

    @Mock
    private GroupInvitationService groupInvitationService;

    @Test
    @DisplayName("cleanup은 retention cutoff로 batch가 가득 찬 동안 독립 batch 삭제를 반복한다")
    void repeatsFullBatchesUntilPartialBatch() {
        // given
        Instant now = Instant.parse("2026-07-28T08:00:00Z");
        Instant cutoff = Instant.parse("2026-07-27T08:00:00Z");
        GroupInvitationProperties properties = properties(2, 10);
        GroupInvitationCleanupScheduler scheduler = new GroupInvitationCleanupScheduler(
                groupInvitationService,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(groupInvitationService.deleteExpiredBatch(cutoff)).thenReturn(2, 2, 1);

        // when
        scheduler.deleteRetainedExpiredInvitations();

        // then
        verify(groupInvitationService, times(3)).deleteExpiredBatch(cutoff);
    }

    @Test
    @DisplayName("cleanup은 backlog가 있어도 configured run batch 상한에서 멈춘다")
    void stopsAtConfiguredBatchLimit() {
        // given
        Instant now = Instant.parse("2026-07-28T08:00:00Z");
        Instant cutoff = Instant.parse("2026-07-27T08:00:00Z");
        GroupInvitationProperties properties = properties(2, 3);
        GroupInvitationCleanupScheduler scheduler = new GroupInvitationCleanupScheduler(
                groupInvitationService,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(groupInvitationService.deleteExpiredBatch(cutoff)).thenReturn(2);

        // when
        scheduler.deleteRetainedExpiredInvitations();

        // then
        verify(groupInvitationService, times(3)).deleteExpiredBatch(cutoff);
    }

    @Test
    @DisplayName("cleanup 실패는 원인을 error 로그로 남기고 스케줄러 밖으로 전파하지 않는다")
    void logsCleanupFailureWithoutPropagating(CapturedOutput output) {
        // given
        Instant now = Instant.parse("2026-07-28T08:00:00Z");
        Instant cutoff = Instant.parse("2026-07-27T08:00:00Z");
        IllegalStateException cause = new IllegalStateException("cleanup failed");
        GroupInvitationCleanupScheduler scheduler = new GroupInvitationCleanupScheduler(
                groupInvitationService,
                properties(2, 3),
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(groupInvitationService.deleteExpiredBatch(cutoff)).thenThrow(cause);

        // when, then
        assertDoesNotThrow(scheduler::deleteRetainedExpiredInvitations);
        assertThat(output.getOut())
                .contains("Group invitation cleanup failed. message=cleanup failed")
                .contains("java.lang.IllegalStateException: cleanup failed");
    }

    private GroupInvitationProperties properties(int batchSize, int maxBatches) {
        GroupInvitationProperties properties = new GroupInvitationProperties();
        properties.setExpiredRetention(Duration.ofHours(24));
        properties.setCleanupBatchSize(batchSize);
        properties.setCleanupMaxBatchesPerRun(maxBatches);
        return properties;
    }
}
