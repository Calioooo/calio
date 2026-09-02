package com.calio.calendar.vote.service;

import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.repository.VoteRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VoteCommandService {

    private final VoteRepository voteRepository;

    public VoteCommandService(VoteRepository voteRepository) {
        this.voteRepository = voteRepository;
    }

    public void replaceVotes(VoteParticipant participant, List<Vote> votes) {
        voteRepository.deleteAllByVoteParticipantId(participant.getId());
        voteRepository.saveAll(votes);
        participant.submit();
    }
}
