package com.calio.calendar.vote.service;

import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteParticipantQueryService {

    private final VoteParticipantRepository voteParticipantRepository;
    private final VoteRepository voteRepository;

    public VoteParticipantQueryService(
            VoteParticipantRepository voteParticipantRepository,
            VoteRepository voteRepository
    ) {
        this.voteParticipantRepository = voteParticipantRepository;
        this.voteRepository = voteRepository;
    }

    public Optional<VoteParticipant> getParticipantByVoteRoomPublicIdAndNicknameIfExists(
            UUID voteRoomPublicId,
            String nickname
    ) {
        return voteParticipantRepository.findByVoteRoomPublicIdAndNickname(voteRoomPublicId, nickname);
    }

    public List<LocalDate> listUnavailableDatesByVoteParticipantId(Long voteParticipantId) {
        return voteRepository.findAllByVoteParticipantId(voteParticipantId)
                .stream()
                .map(vote -> vote.getUnavailableDate())
                .toList();
    }
}
