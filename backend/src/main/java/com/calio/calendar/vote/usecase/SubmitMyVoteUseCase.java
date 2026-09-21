package com.calio.calendar.vote.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.VoteSubmissionResponse;
import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmitMyVoteUseCase {

  private final VoteParticipantRepository voteParticipantRepository;
  private final VoteRepository voteRepository;
  private final VoteRoomRepository voteRoomRepository;

  public SubmitMyVoteUseCase(
      VoteParticipantRepository voteParticipantRepository,
      VoteRepository voteRepository,
      VoteRoomRepository voteRoomRepository) {
    this.voteParticipantRepository = voteParticipantRepository;
    this.voteRepository = voteRepository;
    this.voteRoomRepository = voteRoomRepository;
  }

  @Transactional
  public VoteSubmissionResponse submit(
      UUID voteRoomPublicId, Long accountId, List<LocalDate> requestedDates) {
    VoteParticipant participant =
        voteParticipantRepository
            .findByVoteRoomPublicIdAndAccountIdForUpdate(voteRoomPublicId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
    VoteRoom voteRoom =
        voteRoomRepository
            .findById(participant.getVoteRoomId())
            .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
    VoteSubmissionDates unavailableDates = VoteSubmissionDates.of(requestedDates);
    if (unavailableDates.hasDateOutside(voteRoom.getCandidateDateRange())) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    voteRepository.deleteAllByVoteParticipantId(participant.getId());
    voteRepository.saveAll(
        unavailableDates.values().stream().map(date -> new Vote(participant, date)).toList());
    participant.submit();
    return VoteSubmissionResponse.from(participant, unavailableDates.values());
  }
}
