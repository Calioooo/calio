package com.calio.calendar.vote.controller.dto;

import com.calio.calendar.vote.domain.VoteRoom;
import java.time.LocalDate;
import java.util.UUID;

public record VoteRoomResponse(UUID publicId, String name, LocalDate candidateStartDate, LocalDate candidateEndDate) {
    public static VoteRoomResponse from(VoteRoom voteRoom) {
        return new VoteRoomResponse(voteRoom.getPublicId(), voteRoom.getName(), voteRoom.getCandidateStartDate(), voteRoom.getCandidateEndDate());
    }
}
