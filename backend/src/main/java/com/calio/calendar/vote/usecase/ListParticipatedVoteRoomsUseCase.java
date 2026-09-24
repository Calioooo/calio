package com.calio.calendar.vote.usecase;

import com.calio.calendar.vote.controller.dto.ParticipatedVoteRoomResponse;
import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListParticipatedVoteRoomsUseCase {

  private final VoteParticipantRepository voteParticipantRepository;
  private final VoteRoomRepository voteRoomRepository;

  public ListParticipatedVoteRoomsUseCase(
      VoteParticipantRepository voteParticipantRepository, VoteRoomRepository voteRoomRepository) {
    this.voteParticipantRepository = voteParticipantRepository;
    this.voteRoomRepository = voteRoomRepository;
  }

  @Transactional(readOnly = true)
  public List<ParticipatedVoteRoomResponse> list(Long accountId) {
    List<VoteParticipant> participants =
        voteParticipantRepository.findByAccountIdOrderByUpdatedAtDesc(accountId);
    Map<Long, VoteRoom> voteRoomsById = findVoteRoomsById(participants);
    return participants.stream()
        .map(
            participant ->
                ParticipatedVoteRoomResponse.from(
                    voteRoomsById.get(participant.getVoteRoomId()), participant))
        .toList();
  }

  private Map<Long, VoteRoom> findVoteRoomsById(List<VoteParticipant> participants) {
    return voteRoomRepository
        .findAllById(participants.stream().map(VoteParticipant::getVoteRoomId).distinct().toList())
        .stream()
        .collect(Collectors.toMap(VoteRoom::getId, Function.identity()));
  }
}
