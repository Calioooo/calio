package com.calio.calendar.vote.service;

import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VoteParticipantCommandService {

    private final VoteParticipantRepository voteParticipantRepository;

    public VoteParticipantCommandService(VoteParticipantRepository voteParticipantRepository) {
        this.voteParticipantRepository = voteParticipantRepository;
    }

    public VoteParticipant create(VoteParticipant participant) {
        return voteParticipantRepository.save(participant);
    }
}
