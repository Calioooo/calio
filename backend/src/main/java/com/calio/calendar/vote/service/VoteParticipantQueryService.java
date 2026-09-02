package com.calio.calendar.vote.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteParticipantQueryService {

    private final VoteRoomRepository voteRoomRepository;
    private final VoteParticipantRepository voteParticipantRepository;

    public VoteParticipantQueryService(
            VoteRoomRepository voteRoomRepository,
            VoteParticipantRepository voteParticipantRepository
    ) {
        this.voteRoomRepository = voteRoomRepository;
        this.voteParticipantRepository = voteParticipantRepository;
    }

    @Transactional
    public VoteRoom getVoteRoomForParticipantCreation(UUID voteRoomPublicId) {
        return voteRoomRepository.findByPublicIdForUpdate(voteRoomPublicId)
                .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
    }

    public Optional<VoteParticipant> findParticipantByVoteRoomPublicIdAndNickname(
            UUID voteRoomPublicId,
            String nickname
    ) {
        return voteParticipantRepository.findByVoteRoomPublicIdAndNickname(voteRoomPublicId, nickname);
    }
}
