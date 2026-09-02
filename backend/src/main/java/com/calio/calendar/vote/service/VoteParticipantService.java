package com.calio.calendar.vote.service;

import com.calio.calendar.common.domain.NicknameFields;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteParticipantService {

    private final VoteParticipantQueryService voteParticipantQueryService;
    private final VoteParticipantCommandService voteParticipantCommandService;
    private final VoteParticipantPasswordHasher passwordHasher;

    public VoteParticipantService(
            VoteParticipantQueryService voteParticipantQueryService,
            VoteParticipantCommandService voteParticipantCommandService,
            VoteParticipantPasswordHasher passwordHasher
    ) {
        this.voteParticipantQueryService = voteParticipantQueryService;
        this.voteParticipantCommandService = voteParticipantCommandService;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public VoteParticipant create(UUID voteRoomPublicId, String nickname, String password) {
        VoteRoom voteRoom = voteParticipantQueryService
                .getVoteRoomForParticipantCreation(voteRoomPublicId);
        String normalizedNickname = NicknameFields.normalize(nickname);
        requireNicknameAvailable(voteRoomPublicId, normalizedNickname);
        String passwordHash = password == null ? null : passwordHasher.hash(password);
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
}
