package com.calio.calendar.vote.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.util.List;
import java.util.UUID;
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

    public VoteRoom getByPublicId(UUID publicId) {
        return voteRoomRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
    }
}
