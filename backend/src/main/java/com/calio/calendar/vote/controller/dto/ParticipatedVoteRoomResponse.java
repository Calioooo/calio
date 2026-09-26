package com.calio.calendar.vote.controller.dto;

import com.calio.calendar.vote.domain.VoteParticipant;
import com.calio.calendar.vote.domain.VoteParticipantStatus;
import com.calio.calendar.vote.domain.VoteRoom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ParticipatedVoteRoomResponse(
    UUID publicId,
    String name,
    LocalDate candidateStartDate,
    LocalDate candidateEndDate,
    String nickname,
    VoteParticipantStatus participantStatus,
    Instant participantUpdatedAt) {

  public static ParticipatedVoteRoomResponse from(VoteRoom voteRoom, VoteParticipant participant) {
    return new ParticipatedVoteRoomResponse(
        voteRoom.getPublicId(),
        voteRoom.getName(),
        voteRoom.getCandidateStartDate(),
        voteRoom.getCandidateEndDate(),
        participant.getNickname(),
        participant.getStatus(),
        participant.getUpdatedAt());
  }
}
