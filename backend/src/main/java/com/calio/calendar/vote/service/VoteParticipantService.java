package com.calio.calendar.vote.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import java.text.Normalizer;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteParticipantService {

    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[A-Za-z0-9가-힣]{1,9}$");

    private final VoteParticipantQueryService voteParticipantQueryService;
    private final VoteParticipantCommandService voteParticipantCommandService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public VoteParticipantService(
            VoteParticipantQueryService voteParticipantQueryService,
            VoteParticipantCommandService voteParticipantCommandService
    ) {
        this.voteParticipantQueryService = voteParticipantQueryService;
        this.voteParticipantCommandService = voteParticipantCommandService;
    }

    @Transactional
    public VoteParticipant create(UUID voteRoomPublicId, String nickname, String password) {
        VoteRoom voteRoom = voteParticipantQueryService
                .getVoteRoomForParticipantCreation(voteRoomPublicId);
        String normalizedNickname = normalizeNickname(nickname);
        requireNicknameAvailable(voteRoomPublicId, normalizedNickname);
        String passwordHash = password == null ? null : hashPassword(password);
        return voteParticipantCommandService.create(
                new VoteParticipant(voteRoom, normalizedNickname, passwordHash)
        );
    }

    private void requireNicknameAvailable(UUID voteRoomPublicId, String nickname) {
        if (voteParticipantQueryService
                .findParticipantByVoteRoomPublicIdAndNickname(voteRoomPublicId, nickname)
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
}
