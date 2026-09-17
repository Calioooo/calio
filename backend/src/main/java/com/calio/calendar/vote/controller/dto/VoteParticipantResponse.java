package com.calio.calendar.vote.controller.dto;

import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantStatus;

public record VoteParticipantResponse(String nickname, VoteParticipantStatus status) {
    public static VoteParticipantResponse from(VoteParticipant participant) {
        return new VoteParticipantResponse(participant.getNickname(), participant.getStatus());
    }
}
