package com.calio.calendar.vote.service;

import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.repository.VoteRepository;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VoteCommandService {

    private final VoteRepository voteRepository;

    public VoteCommandService(VoteRepository voteRepository) {
        this.voteRepository = voteRepository;
    }

    public void replaceUnavailableDates(VoteParticipant participant, Collection<LocalDate> unavailableDates) {
        voteRepository.deleteAllByVoteParticipantId(participant.getId());
        voteRepository.saveAll(unavailableDates.stream()
                .map(date -> new Vote(participant, date))
                .toList());
        participant.submit();
    }
}
