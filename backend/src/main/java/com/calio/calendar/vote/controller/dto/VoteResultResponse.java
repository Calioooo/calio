package com.calio.calendar.vote.controller.dto;

import com.calio.calendar.vote.domain.VoteRoom;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VoteResultResponse(
        UUID publicId,
        String name,
        LocalDate candidateStartDate,
        LocalDate candidateEndDate,
        List<VoteDateResultResponse> dates,
        List<String> submittedNicknames
) {

    public static VoteResultResponse from(
            VoteRoom voteRoom,
            List<VoteDateResultResponse> dates,
            List<String> submittedNicknames
    ) {
        return new VoteResultResponse(
                voteRoom.getPublicId(),
                voteRoom.getName(),
                voteRoom.getCandidateStartDate(),
                voteRoom.getCandidateEndDate(),
                dates,
                submittedNicknames
        );
    }
}
