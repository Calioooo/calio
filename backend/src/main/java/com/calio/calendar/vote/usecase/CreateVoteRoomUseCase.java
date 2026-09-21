package com.calio.calendar.vote.usecase;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.VoteRoomResponse;
import com.calio.calendar.vote.domain.VoteCandidateDateRange;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateVoteRoomUseCase {

  private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
  private final AccountRepository accountRepository;
  private final VoteRoomRepository voteRoomRepository;
  private final Clock clock;

  public CreateVoteRoomUseCase(
      AccountRepository accountRepository, VoteRoomRepository voteRoomRepository, Clock clock) {
    this.accountRepository = accountRepository;
    this.voteRoomRepository = voteRoomRepository;
    this.clock = clock;
  }

  @Transactional
  public VoteRoomResponse create(Long accountId, String name, LocalDate candidateEndDate) {
    LocalDate candidateStartDate = LocalDate.now(clock.withZone(KOREA_ZONE));
    VoteCandidateDateRange candidateDateRange =
        VoteCandidateDateRange.of(candidateStartDate, candidateEndDate);
    accountRepository
        .findById(accountId)
        .orElseThrow(() -> new CalioException(ErrorCode.ACCOUNT_NOT_FOUND));
    VoteRoom voteRoom =
        voteRoomRepository.save(
            new VoteRoom(UUID.randomUUID(), name, candidateDateRange, accountId));
    return VoteRoomResponse.from(voteRoom);
  }
}
