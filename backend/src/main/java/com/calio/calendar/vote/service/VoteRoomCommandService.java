package com.calio.calendar.vote.service;

import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VoteRoomCommandService {
    private final VoteRoomRepository voteRoomRepository;

    public VoteRoomCommandService(VoteRoomRepository voteRoomRepository) {
        this.voteRoomRepository = voteRoomRepository;
    }

    public VoteRoom create(VoteRoom voteRoom) {
        return voteRoomRepository.save(voteRoom);
    }

    public int deleteExpiredVoteRoomsBefore(LocalDate cutoffDate) {
        return voteRoomRepository.deleteExpiredVoteRoomsBefore(cutoffDate);
    }
}
