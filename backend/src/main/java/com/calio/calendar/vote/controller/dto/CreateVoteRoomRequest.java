package com.calio.calendar.vote.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateVoteRoomRequest(
        @NotBlank(message = "투표방 이름은 공백일 수 없습니다.") String name,
        @NotNull(message = "후보 종료일은 필수입니다.") LocalDate candidateEndDate
) {
}
