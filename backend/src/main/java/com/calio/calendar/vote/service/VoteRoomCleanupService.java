package com.calio.calendar.vote.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteRoomCleanupService {

    static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    static final int RETENTION_DAYS = 90;

    private final VoteRoomCommandService voteRoomCommandService;
    private final Clock clock;

    public VoteRoomCleanupService(VoteRoomCommandService voteRoomCommandService, Clock clock) {
        this.voteRoomCommandService = voteRoomCommandService;
        this.clock = clock;
    }

    @Transactional
    public int deleteExpiredVoteRooms() {
        LocalDate cutoffDate = LocalDate.now(clock.withZone(KOREA_ZONE)).minusDays(RETENTION_DAYS);
        return voteRoomCommandService.deleteExpiredVoteRoomsBefore(cutoffDate);
    }
}
