package com.calio.calendar.vote.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.VoteParticipantResponse;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.text.Normalizer;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateVoteParticipantUseCase {

  private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[A-Za-z0-9가-힣]{1,9}$");

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
  public VoteParticipant createParticipant(UUID voteRoomPublicId, String nickname, String password) {
    String normalizedNickname = normalizeNickname(nickname);
    VoteRoom voteRoom =
        voteRoomRepository
            .findByPublicIdForUpdate(voteRoomPublicId)
            .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
    if (voteParticipantRepository
        .findByVoteRoomPublicIdAndNickname(voteRoomPublicId, normalizedNickname)
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

  private String normalizeNickname(String nickname) {
    if (nickname == null) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    String normalizedNickname = Normalizer.normalize(nickname, Normalizer.Form.NFC);
    if (!NICKNAME_PATTERN.matcher(normalizedNickname).matches()) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    return normalizedNickname;
  }

  private String hashPassword(String password) {
    return password == null ? null : passwordEncoder.encode(password);
  }
}
