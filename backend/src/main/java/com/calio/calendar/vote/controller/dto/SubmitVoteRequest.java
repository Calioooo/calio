package com.calio.calendar.vote.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record SubmitVoteRequest(
        @NotBlank(message = "투표 참여자 닉네임은 공백일 수 없습니다.") String nickname,
        String password,
        @NotNull(message = "불가능한 날짜 선택 정보는 필수입니다.") List<@NotNull LocalDate> unavailableDates
) {
}
