package com.calio.calendar.vote.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.VoteParticipantSelectionResponse;
import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantNickname;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LookupVoteParticipantSelectionUseCase {

  private final VoteRoomRepository voteRoomRepository;
  private final VoteParticipantRepository voteParticipantRepository;
  private final VoteRepository voteRepository;
  private final VoteParticipantCredentialVerifier credentialVerifier;

  public LookupVoteParticipantSelectionUseCase(
      VoteRoomRepository voteRoomRepository,
      VoteParticipantRepository voteParticipantRepository,
      VoteRepository voteRepository,
      VoteParticipantCredentialVerifier credentialVerifier) {
    this.voteRoomRepository = voteRoomRepository;
    this.voteParticipantRepository = voteParticipantRepository;
    this.voteRepository = voteRepository;
    this.credentialVerifier = credentialVerifier;
  }

  @Transactional(readOnly = true)
  public VoteParticipantSelectionResponse lookup(UUID voteRoomPublicId, String nickname, String password) {
    voteRoomRepository
        .findByPublicId(voteRoomPublicId)
        .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
    VoteParticipant participant =
        voteParticipantRepository
            .findByVoteRoomPublicIdAndNickname(voteRoomPublicId, VoteParticipantNickname.of(nickname).value())
            .orElseThrow(() -> new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
    credentialVerifier.verify(participant, password);
    return VoteParticipantSelectionResponse.from(participant, getUnavailableDates(participant));
  }

  private List<LocalDate> getUnavailableDates(VoteParticipant participant) {
    if (!participant.hasSubmittedVotes()) {
      return List.of();
    }
    return voteRepository.findAllByVoteParticipantId(participant.getId()).stream()
        .map(Vote::getUnavailableDate)
        .toList();
  }
}
