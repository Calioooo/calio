package com.calio.calendar.groupinvitation.scheduler;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.service.GroupInvitationCommandService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GroupInvitationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(GroupInvitationCleanupScheduler.class);

    private final GroupInvitationCommandService commandService;
    private final GroupInvitationProperties properties;
    private final Clock clock;

    public GroupInvitationCleanupScheduler(
            GroupInvitationCommandService commandService,
            GroupInvitationProperties properties,
            Clock clock
    ) {
        this.commandService = commandService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${group-invitation.cleanup-fixed-delay:PT1H}")
    public void deleteRetainedExpiredInvitations() {
        try {
            Instant cutoff = clock.instant().minus(properties.getExpiredRetention());
            int deletedCount = deleteBatches(cutoff);
            log.info("Group invitation cleanup finished. deletedCount={}", deletedCount);
        } catch (Exception exception) {
            throw new CalioException(ErrorCode.GROUP_INVITATION_CLEANUP_FAILED, exception);
        }
    }

    private int deleteBatches(Instant cutoff) {
        int totalDeleted = 0;
        for (int batch = 0; batch < properties.getCleanupMaxBatchesPerRun(); batch++) {
            int deleted = commandService.deleteExpiredBatch(cutoff);
            totalDeleted += deleted;
            if (deleted < properties.getCleanupBatchSize()) {
                break;
            }
        }
        return totalDeleted;
    }
}
