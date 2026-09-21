package com.calio.calendar.vote.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.VoteParticipantResponse;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantNickname;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateMyVoteParticipantUseCase {

  private final VoteRoomRepository voteRoomRepository;
  private final VoteParticipantRepository voteParticipantRepository;

  public CreateMyVoteParticipantUseCase(
      VoteRoomRepository voteRoomRepository, VoteParticipantRepository voteParticipantRepository) {
    this.voteRoomRepository = voteRoomRepository;
    this.voteParticipantRepository = voteParticipantRepository;
  }

  @Transactional
  public VoteParticipantResponse create(UUID voteRoomPublicId, Long accountId, String nickname) {
    VoteParticipantNickname normalizedNickname = VoteParticipantNickname.of(nickname);
    VoteRoom voteRoom =
        voteRoomRepository
            .findForUpdateByPublicId(voteRoomPublicId)
            .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
    if (voteParticipantRepository
        .findByVoteRoomPublicIdAndAccountId(voteRoomPublicId, accountId)
        .isPresent()) {
      throw new CalioException(ErrorCode.VOTE_PARTICIPANT_ALREADY_EXISTS);
    }
    if (voteParticipantRepository
        .findByVoteRoomPublicIdAndNickname(voteRoomPublicId, normalizedNickname.value())
        .isPresent()) {
      throw new CalioException(ErrorCode.VOTE_PARTICIPANT_NICKNAME_CONFLICT);
    }
    try {
      return VoteParticipantResponse.from(
          voteParticipantRepository.save(
              VoteParticipant.forAccount(voteRoom.getId(), normalizedNickname, accountId)));
    } catch (DataIntegrityViolationException exception) {
      throw new CalioException(ErrorCode.VOTE_PARTICIPANT_ALREADY_EXISTS, exception);
    }
  }
}
