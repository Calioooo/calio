package com.calio.calendar.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateRecurrenceOccurrenceRequest(
        @NotNull(message = "반복 일정 원본 시작 시각은 필수입니다.") Instant originStartAt,
        @NotNull(message = "반복 일정 수정 시작 시각은 필수입니다.") Instant startAt,
        @NotNull(message = "반복 일정 수정 종료 시각은 필수입니다.") Instant endAt
) {
}
