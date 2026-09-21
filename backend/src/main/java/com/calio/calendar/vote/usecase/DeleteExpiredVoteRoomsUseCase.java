package com.calio.calendar.vote.usecase;

import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteExpiredVoteRoomsUseCase {

  static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
  static final int RETENTION_DAYS = 90;

  private final VoteRoomRepository voteRoomRepository;
  private final Clock clock;

  public DeleteExpiredVoteRoomsUseCase(VoteRoomRepository voteRoomRepository, Clock clock) {
    this.voteRoomRepository = voteRoomRepository;
    this.clock = clock;
  }

  @Transactional
  public int deleteExpiredVoteRooms() {
    LocalDate cutoffDate = LocalDate.now(clock.withZone(KOREA_ZONE)).minusDays(RETENTION_DAYS);
    return voteRoomRepository.deleteExpiredVoteRoomsBefore(cutoffDate);
  }
}
