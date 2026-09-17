package com.calio.calendar.vote.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.LookupVoteParticipantSelectionRequest;
import com.calio.calendar.vote.controller.dto.VoteParticipantSelectionResponse;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteParticipantSelectionLookupService {

  private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[A-Za-z0-9가-힣]{1,9}$");

  private final VoteRoomRepository voteRoomRepository;
  private final VoteParticipantRepository voteParticipantRepository;
  private final VoteRepository voteRepository;
  private final PasswordEncoder passwordEncoder;

  public VoteParticipantSelectionLookupService(
      VoteRoomRepository voteRoomRepository,
      VoteParticipantRepository voteParticipantRepository,
      VoteRepository voteRepository,
      PasswordEncoder passwordEncoder) {
    this.voteRoomRepository = voteRoomRepository;
    this.voteParticipantRepository = voteParticipantRepository;
    this.voteRepository = voteRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public VoteParticipantSelectionResponse lookup(
      UUID voteRoomPublicId, LookupVoteParticipantSelectionRequest request) {
    requireVoteRoom(voteRoomPublicId);
    VoteParticipant participant = getParticipant(voteRoomPublicId, request.nickname());
    requireValidPassword(participant, request.password());
    return VoteParticipantSelectionResponse.from(participant, getUnavailableDates(participant));
  }

  private void requireVoteRoom(UUID voteRoomPublicId) {
    voteRoomRepository
        .findByPublicId(voteRoomPublicId)
        .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
  }

  private VoteParticipant getParticipant(UUID voteRoomPublicId, String nickname) {
    return voteParticipantRepository
        .findByVoteRoomPublicIdAndNickname(voteRoomPublicId, normalizeNickname(nickname))
        .orElseThrow(() -> new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
  }

  private String normalizeNickname(String nickname) {
    if (nickname == null) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    String normalized = Normalizer.normalize(nickname, Normalizer.Form.NFC);
    if (!NICKNAME_PATTERN.matcher(normalized).matches()) {
      throw new CalioException(ErrorCode.VALIDATION_FAILED);
    }
    return normalized;
  }

  private void requireValidPassword(VoteParticipant participant, String password) {
    if (participant.getPasswordHash() != null
        && (password == null
            || !passwordEncoder.matches(password, participant.getPasswordHash()))) {
      throw new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID);
    }
  }

  private List<LocalDate> getUnavailableDates(VoteParticipant participant) {
    if (!participant.hasSubmittedVotes()) {
      return List.of();
    }
    return voteRepository.findAllByVoteParticipantId(participant.getId()).stream()
        .map(vote -> vote.getUnavailableDate())
        .toList();
  }
}
