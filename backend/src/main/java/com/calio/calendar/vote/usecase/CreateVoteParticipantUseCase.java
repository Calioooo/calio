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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateVoteParticipantUseCase {

  private final VoteRoomRepository voteRoomRepository;
  private final VoteParticipantRepository voteParticipantRepository;
  private final PasswordEncoder passwordEncoder;

  public CreateVoteParticipantUseCase(
      VoteRoomRepository voteRoomRepository,
      VoteParticipantRepository voteParticipantRepository,
      PasswordEncoder passwordEncoder) {
    this.voteRoomRepository = voteRoomRepository;
    this.voteParticipantRepository = voteParticipantRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public VoteParticipantResponse create(UUID voteRoomPublicId, String nickname, String password) {
    return VoteParticipantResponse.from(createParticipant(voteRoomPublicId, nickname, password));
  }

  @Transactional
  public VoteParticipant createParticipant(
      UUID voteRoomPublicId, String nickname, String password) {
    VoteParticipantNickname normalizedNickname = VoteParticipantNickname.of(nickname);
    VoteRoom voteRoom =
        voteRoomRepository
            .findForUpdateByPublicId(voteRoomPublicId)
            .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
    if (voteParticipantRepository
        .findByVoteRoomPublicIdAndNickname(voteRoomPublicId, normalizedNickname.value())
        .isPresent()) {
      throw new CalioException(ErrorCode.VOTE_PARTICIPANT_NICKNAME_CONFLICT);
    }
    try {
      return voteParticipantRepository.save(
          new VoteParticipant(voteRoom, normalizedNickname, hashPassword(password)));
    } catch (DataIntegrityViolationException exception) {
      throw new CalioException(ErrorCode.VOTE_PARTICIPANT_NICKNAME_CONFLICT, exception);
    }
  }

  private String hashPassword(String password) {
    return password == null ? null : passwordEncoder.encode(password);
  }
}
