package com.calio.calendar.vote.controller.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record SubmitMyVoteRequest(
    @NotNull(message = "불가능한 날짜 선택 정보는 필수입니다.") List<@NotNull LocalDate> unavailableDates) {}
