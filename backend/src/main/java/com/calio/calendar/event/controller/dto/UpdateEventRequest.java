package com.calio.calendar.event.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateEventRequest(
        @NotBlank(message = "이벤트 제목은 공백일 수 없습니다.") String title,
        String description,
        @NotNull(message = "이벤트 시작 시각은 필수입니다.") Instant startAt,
        @NotNull(message = "이벤트 종료 시각은 필수입니다.") Instant endAt,
        Long tagId
) {
}
