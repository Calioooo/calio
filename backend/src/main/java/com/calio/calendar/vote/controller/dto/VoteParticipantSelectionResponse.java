package com.calio.calendar.vote.controller.dto;

import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantStatus;
import java.time.LocalDate;
import java.util.List;

public record VoteParticipantSelectionResponse(
        String nickname,
        VoteParticipantStatus status,
        List<LocalDate> unavailableDates
) {
    public static VoteParticipantSelectionResponse from(
            VoteParticipant participant,
            List<LocalDate> unavailableDates
    ) {
        return new VoteParticipantSelectionResponse(
                participant.getNickname(),
                participant.getStatus(),
                unavailableDates
        );
    }
}
