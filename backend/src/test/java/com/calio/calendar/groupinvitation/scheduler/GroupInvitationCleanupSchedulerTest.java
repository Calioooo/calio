package com.calio.calendar.groupinvitation.scheduler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.service.GroupInvitationCleanupService;
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
    private GroupInvitationCleanupService cleanupService;

    @Test
    @DisplayName("cleanup은 retention cutoff로 batch가 가득 찬 동안 독립 batch 삭제를 반복한다")
    void repeatsFullBatchesUntilPartialBatch() {
        // given
        Instant now = Instant.parse("2026-07-28T08:00:00Z");
        Instant cutoff = Instant.parse("2026-07-27T08:00:00Z");
        GroupInvitationProperties properties = properties(2, 10);
        GroupInvitationCleanupScheduler scheduler = new GroupInvitationCleanupScheduler(
                cleanupService,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(cleanupService.deleteBatch(cutoff)).thenReturn(2, 2, 1);

        // when
        scheduler.deleteRetainedExpiredInvitations();

        // then
        verify(cleanupService, times(3)).deleteBatch(cutoff);
    }

    @Test
    @DisplayName("cleanup은 backlog가 있어도 configured run batch 상한에서 멈춘다")
    void stopsAtConfiguredBatchLimit() {
        // given
        Instant now = Instant.parse("2026-07-28T08:00:00Z");
        Instant cutoff = Instant.parse("2026-07-27T08:00:00Z");
        GroupInvitationProperties properties = properties(2, 3);
        GroupInvitationCleanupScheduler scheduler = new GroupInvitationCleanupScheduler(
                cleanupService,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(cleanupService.deleteBatch(cutoff)).thenReturn(2);

        // when
        scheduler.deleteRetainedExpiredInvitations();

        // then
        verify(cleanupService, times(3)).deleteBatch(cutoff);
    }

    private GroupInvitationProperties properties(int batchSize, int maxBatches) {
        GroupInvitationProperties properties = new GroupInvitationProperties();
        properties.setExpiredRetention(Duration.ofHours(24));
        properties.setCleanupBatchSize(batchSize);
        properties.setCleanupMaxBatchesPerRun(maxBatches);
        return properties;
    }
}
