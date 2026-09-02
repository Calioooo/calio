package com.calio.calendar.vote.service;

import com.calio.calendar.vote.controller.dto.VoteDateResultResponse;
import com.calio.calendar.vote.controller.dto.VoteResultResponse;
import com.calio.calendar.vote.domain.VoteRoom;
import com.calio.calendar.vote.repository.VoteDateCountProjection;
import com.calio.calendar.vote.repository.VoteDateNicknameProjection;
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
        Map<LocalDate, Long> unavailableCounts = voteResultQueryService.listSubmittedVoteDateCounts(publicId)
                .stream()
                .collect(Collectors.toMap(
                        VoteDateCountProjection::unavailableDate,
                        VoteDateCountProjection::unavailableCount
                ));
        Map<LocalDate, List<String>> unavailableNicknames = voteResultQueryService
                .listSubmittedVoteDateNicknames(publicId)
                .stream()
                .collect(Collectors.groupingBy(
                        VoteDateNicknameProjection::unavailableDate,
                        Collectors.mapping(VoteDateNicknameProjection::nickname, Collectors.toList())
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
