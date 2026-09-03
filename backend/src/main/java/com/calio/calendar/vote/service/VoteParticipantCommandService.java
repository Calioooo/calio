package com.calio.calendar.vote.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VoteParticipantCommandService {

    private final VoteParticipantRepository voteParticipantRepository;
    private final VoteRoomRepository voteRoomRepository;

    public VoteParticipantCommandService(
            VoteParticipantRepository voteParticipantRepository,
            VoteRoomRepository voteRoomRepository
    ) {
        this.voteParticipantRepository = voteParticipantRepository;
        this.voteRoomRepository = voteRoomRepository;
    }

    public VoteRoom getVoteRoomForParticipantCreation(UUID voteRoomPublicId) {
        return voteRoomRepository.findByPublicIdForUpdate(voteRoomPublicId)
                .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
    }

    public VoteParticipant create(VoteParticipant participant) {
        return voteParticipantRepository.save(participant);
    }
}
