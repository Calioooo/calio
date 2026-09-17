package com.calio.calendar.vote.controller.dto;

import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantStatus;
import java.time.LocalDate;
import java.util.List;

public record VoteSubmissionResponse(
        String nickname,
        VoteParticipantStatus status,
        List<LocalDate> unavailableDates
) {
    public static VoteSubmissionResponse from(VoteParticipant participant, List<LocalDate> unavailableDates) {
        return new VoteSubmissionResponse(participant.getNickname(), participant.getStatus(), unavailableDates);
    }
}
