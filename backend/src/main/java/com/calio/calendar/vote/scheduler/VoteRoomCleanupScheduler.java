package com.calio.calendar.vote.scheduler;

import com.calio.calendar.vote.service.VoteRoomCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class VoteRoomCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(VoteRoomCleanupScheduler.class);

    private final VoteRoomCleanupService voteRoomCleanupService;

    public VoteRoomCleanupScheduler(VoteRoomCleanupService voteRoomCleanupService) {
        this.voteRoomCleanupService = voteRoomCleanupService;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void deleteExpiredVoteRooms() {
        try {
            int deletedCount = voteRoomCleanupService.deleteExpiredVoteRooms();
            log.info("VoteRoom cleanup finished. deletedCount={}", deletedCount);
        } catch (Exception exception) {
            log.error("VoteRoom cleanup failed. message={}", exception.getMessage(), exception);
        }
    }
}
