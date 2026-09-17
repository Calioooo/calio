package com.calio.calendar.vote.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateVoteParticipantRequest(
        @NotBlank(message = "투표 참여자 닉네임은 공백일 수 없습니다.") String nickname,
        String password
) {
}
