package com.calio.calendar.vote.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.CreateVoteParticipantRequest;
import com.calio.calendar.vote.controller.dto.SubmitVoteRequest;
import com.calio.calendar.vote.controller.dto.VoteParticipantResponse;
import com.calio.calendar.vote.controller.dto.VoteSubmissionResponse;
import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import java.time.LocalDate;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteParticipantService {

    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[A-Za-z0-9가-힣]{1,9}$");

    private final VoteParticipantQueryService voteParticipantQueryService;
    private final VoteParticipantCommandService voteParticipantCommandService;
    private final VoteCommandService voteCommandService;
    private final PasswordEncoder passwordEncoder;

    public VoteParticipantService(
            VoteParticipantQueryService voteParticipantQueryService,
            VoteParticipantCommandService voteParticipantCommandService,
            VoteCommandService voteCommandService,
            PasswordEncoder passwordEncoder
    ) {
        this.voteParticipantQueryService = voteParticipantQueryService;
        this.voteParticipantCommandService = voteParticipantCommandService;
        this.voteCommandService = voteCommandService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public VoteParticipantResponse create(UUID voteRoomPublicId, CreateVoteParticipantRequest request) {
        return VoteParticipantResponse.from(create(voteRoomPublicId, request.nickname(), request.password()));
    }

    @Transactional
    public VoteParticipant create(UUID voteRoomPublicId, String nickname, String password) {
        String normalizedNickname = normalizeNickname(nickname);
        VoteRoom voteRoom = voteParticipantCommandService
                .getVoteRoomForParticipantCreation(voteRoomPublicId);
        requireNicknameAvailable(voteRoomPublicId, normalizedNickname);
        String passwordHash = password == null ? null : hashPassword(password);
        return voteParticipantCommandService.create(
                new VoteParticipant(voteRoom, normalizedNickname, passwordHash)
        );
    }

    @Transactional
    public VoteSubmissionResponse submitVotes(UUID voteRoomPublicId, SubmitVoteRequest request) {
        String nickname = normalizeNickname(request.nickname());
        VoteParticipant participant = voteParticipantQueryService
                .getParticipantByVoteRoomPublicIdAndNicknameIfExists(voteRoomPublicId, nickname)
                .orElseThrow(() -> new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID));
        requireValidPassword(participant, request.password());
        VoteParticipant lockedParticipant = voteParticipantCommandService
                .getParticipantForVoteSubmission(voteRoomPublicId, nickname);
        List<LocalDate> unavailableDates = new LinkedHashSet<>(request.unavailableDates()).stream()
                .sorted()
                .toList();
        requireDatesInCandidateRange(lockedParticipant.getVoteRoom(), unavailableDates);
        List<Vote> votes = unavailableDates.stream()
                .map(date -> new Vote(lockedParticipant, date))
                .toList();
        voteCommandService.replaceVotes(lockedParticipant, votes);
        return VoteSubmissionResponse.from(lockedParticipant, unavailableDates);
    }

    private void requireNicknameAvailable(UUID voteRoomPublicId, String nickname) {
        if (voteParticipantQueryService
                .getParticipantByVoteRoomPublicIdAndNicknameIfExists(voteRoomPublicId, nickname)
                .isPresent()) {
            throw new CalioException(ErrorCode.VOTE_PARTICIPANT_NICKNAME_CONFLICT);
        }
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null) {
            throw validationFailed();
        }
        String normalized = Normalizer.normalize(nickname, Normalizer.Form.NFC);
        if (!NICKNAME_PATTERN.matcher(normalized).matches()) {
            throw validationFailed();
        }
        return normalized;
    }

    private CalioException validationFailed() {
        return new CalioException(ErrorCode.VALIDATION_FAILED);
    }

    private String hashPassword(String password) {
        return passwordEncoder.encode(password);
    }

    private void requireValidPassword(VoteParticipant participant, String password) {
        if (participant.getPasswordHash() != null && (password == null
                || !passwordEncoder.matches(password, participant.getPasswordHash()))) {
            throw new CalioException(ErrorCode.VOTE_PARTICIPANT_CREDENTIAL_INVALID);
        }
    }

    private void requireDatesInCandidateRange(VoteRoom voteRoom, List<LocalDate> dates) {
        if (dates.stream().anyMatch(date -> date.isBefore(voteRoom.getCandidateStartDate())
                || date.isAfter(voteRoom.getCandidateEndDate()))) {
            throw validationFailed();
        }
    }
}
