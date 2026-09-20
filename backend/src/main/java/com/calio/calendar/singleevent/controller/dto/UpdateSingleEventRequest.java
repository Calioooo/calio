package com.calio.calendar.singleevent.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateSingleEventRequest(
    @NotNull(message = "이벤트 제목은 필수입니다.") @Size(max = 255, message = "이벤트 제목은 255자 이하여야 합니다.")
        String title,
    String description,
    @NotNull(message = "이벤트 시작 시각은 필수입니다.") Instant startAt,
    @NotNull(message = "이벤트 종료 시각은 필수입니다.") Instant endAt,
    @NotNull(message = "종일 일정 여부는 필수입니다.") Boolean allDay,
    String timeZone,
    Long tagId) {}
