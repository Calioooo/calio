package com.calio.calendar.vote.usecase;

import com.calio.calendar.vote.controller.dto.VoteRoomResponse;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListMyVoteRoomsUseCase {

  private final VoteRoomRepository voteRoomRepository;

  public ListMyVoteRoomsUseCase(VoteRoomRepository voteRoomRepository) {
    this.voteRoomRepository = voteRoomRepository;
  }

  @Transactional(readOnly = true)
  public List<VoteRoomResponse> list(Long accountId) {
    return voteRoomRepository.findAllByCreatedByAccountId(accountId).stream()
        .map(VoteRoomResponse::from)
        .toList();
  }
}
