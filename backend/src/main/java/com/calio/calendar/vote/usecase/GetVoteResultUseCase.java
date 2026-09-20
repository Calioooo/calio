package com.calio.calendar.vote.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.vote.controller.dto.VoteDateResultResponse;
import com.calio.calendar.vote.controller.dto.VoteResultResponse;
import com.calio.calendar.vote.domain.Vote;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteParticipantRepository;
import com.calio.calendar.vote.repository.VoteRepository;
import com.calio.calendar.vote.repository.VoteRoomRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetVoteResultUseCase {

  private final VoteRoomRepository voteRoomRepository;
  private final VoteParticipantRepository voteParticipantRepository;
  private final VoteRepository voteRepository;

  public GetVoteResultUseCase(
      VoteRoomRepository voteRoomRepository,
      VoteParticipantRepository voteParticipantRepository,
      VoteRepository voteRepository) {
    this.voteRoomRepository = voteRoomRepository;
    this.voteParticipantRepository = voteParticipantRepository;
    this.voteRepository = voteRepository;
  }

  @Transactional(readOnly = true)
  public VoteResultResponse get(UUID voteRoomPublicId) {
    VoteRoom voteRoom =
        voteRoomRepository
            .findByPublicId(voteRoomPublicId)
            .orElseThrow(() -> new CalioException(ErrorCode.VOTE_ROOM_NOT_FOUND));
    Map<LocalDate, List<String>> unavailableNicknames =
        voteRepository.findAllSubmittedByVoteRoomPublicId(voteRoomPublicId).stream()
            .collect(
                Collectors.groupingBy(
                    Vote::getUnavailableDate,
                    Collectors.mapping(
                        vote -> vote.getVoteParticipant().getNickname(), Collectors.toList())));
    List<VoteDateResultResponse> dates =
        voteRoom
            .getCandidateStartDate()
            .datesUntil(voteRoom.getCandidateEndDate().plusDays(1))
            .map(
                date -> {
                  List<String> nicknames = unavailableNicknames.getOrDefault(date, List.of());
                  return VoteDateResultResponse.from(date, nicknames.size(), nicknames);
                })
            .toList();
    return VoteResultResponse.from(
        voteRoom,
        dates,
        voteParticipantRepository.findSubmittedNicknamesByVoteRoomPublicId(voteRoomPublicId));
  }
}
