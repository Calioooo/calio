package com.calio.calendar.vote.service;

import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteRoomQueryService {
    private final VoteRoomRepository voteRoomRepository;

    public VoteRoomQueryService(VoteRoomRepository voteRoomRepository) {
        this.voteRoomRepository = voteRoomRepository;
    }

    public List<VoteRoom> listByCreatedByAccountId(Long accountId) {
        return voteRoomRepository.findAllByCreatedByAccountId(accountId);
    }
}
