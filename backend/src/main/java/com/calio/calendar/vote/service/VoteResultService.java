package com.calio.calendar.vote.service;

import com.calio.calendar.vote.controller.dto.VoteDateResultResponse;
import com.calio.calendar.vote.controller.dto.VoteResultResponse;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.domain.Vote;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VoteResultService {

    private final VoteResultQueryService voteResultQueryService;

    public VoteResultService(VoteResultQueryService voteResultQueryService) {
        this.voteResultQueryService = voteResultQueryService;
    }

    public VoteResultResponse getResult(UUID publicId) {
        VoteRoom voteRoom = voteResultQueryService.getVoteRoom(publicId);
        List<Vote> submittedVotes = voteResultQueryService.listSubmittedVotes(publicId);
        Map<LocalDate, Long> unavailableCounts = submittedVotes
                .stream()
                .collect(Collectors.toMap(
                        Vote::getUnavailableDate,
                        vote -> 1L,
                        Long::sum
                ));
        Map<LocalDate, List<String>> unavailableNicknames = submittedVotes
                .stream()
                .collect(Collectors.groupingBy(
                        Vote::getUnavailableDate,
                        Collectors.mapping(vote -> vote.getVoteParticipant().getNickname(), Collectors.toList())
                ));

        List<VoteDateResultResponse> dates = voteRoom.getCandidateStartDate()
                .datesUntil(voteRoom.getCandidateEndDate().plusDays(1))
                .map(date -> VoteDateResultResponse.from(
                        date,
                        unavailableCounts.getOrDefault(date, 0L),
                        unavailableNicknames.getOrDefault(date, List.of())
                ))
                .toList();
        return VoteResultResponse.from(voteRoom, dates, voteResultQueryService.listSubmittedNicknames(publicId));
    }
}
