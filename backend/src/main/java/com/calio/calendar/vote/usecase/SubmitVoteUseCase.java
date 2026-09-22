package com.calio.calendar.vote.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.VoteSubmissionResponse;
import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantNickname;
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
public class SubmitVoteUseCase {

  private final VoteParticipantRepository voteParticipantRepository;
  private final VoteRepository voteRepository;
  private final VoteRoomRepository voteRoomRepository;
  private final VoteParticipantCredentialVerifier credentialVerifier;

  public SubmitVoteUseCase(
      VoteParticipantRepository voteParticipantRepository,
      VoteRepository voteRepository,
      VoteRoomRepository voteRoomRepository,
      VoteParticipantCredentialVerifier credentialVerifier) {
    this.voteParticipantRepository = voteParticipantRepository;
    this.voteRepository = voteRepository;
    this.voteRoomRepository = voteRoomRepository;
    this.credentialVerifier = credentialVerifier;
  }

  @Transactional
  public VoteSubmissionResponse submit(
      UUID voteRoomPublicId, String nickname, String password, List<LocalDate> requestedDates) {
    return submit(voteRoomPublicId, null, nickname, password, requestedDates);
  }

  @Transactional
  public VoteSubmissionResponse submit(
      UUID voteRoomPublicId, Long accountId, List<LocalDate> requestedDates) {
    return submit(voteRoomPublicId, accountId, null, null, requestedDates);
  }

  private VoteSubmissionResponse submit(
      UUID voteRoomPublicId,
      Long accountId,
      String nickname,
      String password,
      List<LocalDate> requestedDates) {
    VoteParticipant lockedParticipant =
        findParticipantForSubmission(voteRoomPublicId, accountId, nickname, password);
    VoteRoom voteRoom =
        voteRoomRepository
            .findById(lockedParticipant.getVoteRoomId())
            .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
    VoteSubmissionDates unavailableDates = VoteSubmissionDates.of(requestedDates);
    if (unavailableDates.hasDateOutside(voteRoom.getCandidateDateRange())) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    voteRepository.deleteAllByVoteParticipantId(lockedParticipant.getId());
    voteRepository.saveAll(
        unavailableDates.values().stream().map(date -> new Vote(lockedParticipant, date)).toList());
    lockedParticipant.submit();
    return VoteSubmissionResponse.from(lockedParticipant, unavailableDates.values());
  }

  private VoteParticipant findParticipantForSubmission(
      UUID voteRoomPublicId, Long accountId, String nickname, String password) {
    if (accountId != null) {
      return voteParticipantRepository
          .findByVoteRoomPublicIdAndAccountIdForUpdate(voteRoomPublicId, accountId)
          .orElseThrow(() -> new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
    }

    VoteParticipantNickname normalizedNickname = VoteParticipantNickname.of(nickname);
    VoteParticipant participant =
        voteParticipantRepository
            .findByVoteRoomPublicIdAndNickname(voteRoomPublicId, normalizedNickname.value())
            .orElseThrow(() -> new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
    credentialVerifier.verify(participant, password);
    return voteParticipantRepository
        .findByVoteRoomPublicIdAndNicknameForUpdate(voteRoomPublicId, normalizedNickname.value())
        .orElseThrow(() -> new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
  }
}
