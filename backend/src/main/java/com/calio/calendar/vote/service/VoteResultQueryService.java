package com.calio.calendar.vote.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteResultQueryService {

    private final VoteRoomRepository voteRoomRepository;
    private final VoteParticipantRepository voteParticipantRepository;
    private final VoteRepository voteRepository;

    public VoteResultQueryService(
            VoteRoomRepository voteRoomRepository,
            VoteParticipantRepository voteParticipantRepository,
            VoteRepository voteRepository
    ) {
        this.voteRoomRepository = voteRoomRepository;
        this.voteParticipantRepository = voteParticipantRepository;
        this.voteRepository = voteRepository;
    }

    public VoteRoom getVoteRoom(UUID publicId) {
        return voteRoomRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
    }

    public List<Vote> listSubmittedVotes(UUID publicId) {
        return voteRepository.findAllSubmittedByVoteRoomPublicId(publicId);
    }

    public List<String> listSubmittedNicknames(UUID publicId) {
        return voteParticipantRepository.findSubmittedNicknamesByVoteRoomPublicId(publicId);
    }
}
