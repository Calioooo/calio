package com.calio.calendar.vote.service;

import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteParticipantQueryService {

    private final VoteParticipantRepository voteParticipantRepository;

    public VoteParticipantQueryService(VoteParticipantRepository voteParticipantRepository) {
        this.voteParticipantRepository = voteParticipantRepository;
    }

    public Optional<VoteParticipant> getParticipantByVoteRoomPublicIdAndNicknameIfExists(
            UUID voteRoomPublicId,
            String nickname
    ) {
        return voteParticipantRepository.findByVoteRoomPublicIdAndNickname(voteRoomPublicId, nickname);
    }

}
