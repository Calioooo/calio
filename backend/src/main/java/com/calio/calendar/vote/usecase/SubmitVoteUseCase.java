package com.calio.calendar.vote.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.VoteSubmissionResponse;
import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantNickname;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmitVoteUseCase {

  private final VoteParticipantRepository voteParticipantRepository;
  private final VoteRepository voteRepository;
  private final VoteParticipantCredentialVerifier credentialVerifier;

  public SubmitVoteUseCase(
      VoteParticipantRepository voteParticipantRepository,
      VoteRepository voteRepository,
      VoteParticipantCredentialVerifier credentialVerifier) {
    this.voteParticipantRepository = voteParticipantRepository;
    this.voteRepository = voteRepository;
    this.credentialVerifier = credentialVerifier;
  }

  @Transactional
  public VoteSubmissionResponse submit(
      UUID voteRoomPublicId, String nickname, String password, List<LocalDate> requestedDates) {
    VoteParticipantNickname normalizedNickname = VoteParticipantNickname.of(nickname);
    VoteParticipant participant =
        voteParticipantRepository
            .findByVoteRoomPublicIdAndNickname(voteRoomPublicId, normalizedNickname.value())
            .orElseThrow(() -> new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
    credentialVerifier.verify(participant, password);

    VoteParticipant lockedParticipant =
        voteParticipantRepository
            .findByVoteRoomPublicIdAndNicknameForUpdate(
                voteRoomPublicId, normalizedNickname.value())
            .orElseThrow(() -> new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
    List<LocalDate> unavailableDates = normalizeDates(requestedDates);
    if (unavailableDates.stream()
        .anyMatch(date -> !lockedParticipant.getVoteRoom().getCandidateDateRange().contains(date))) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    voteRepository.deleteAllByVoteParticipantId(lockedParticipant.getId());
    voteRepository.saveAll(
        unavailableDates.stream().map(date -> new Vote(lockedParticipant, date)).toList());
    lockedParticipant.submit();
    return VoteSubmissionResponse.from(lockedParticipant, unavailableDates);
  }

  private List<LocalDate> normalizeDates(List<LocalDate> requestedDates) {
    return new LinkedHashSet<>(requestedDates).stream().sorted().toList();
  }

}
