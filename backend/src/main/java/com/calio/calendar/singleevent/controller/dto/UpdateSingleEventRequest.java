package com.calio.calendar.singleevent.controller.dto;

import com.calio.calendar.common.validation.MaxCodePointLength;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateSingleEventRequest(
    @NotNull(message = "이벤트 제목은 필수입니다.")
        @MaxCodePointLength(max = 80, message = "이벤트 제목은 80자 이하여야 합니다.")
        String title,
    String description,
    @NotNull(message = "이벤트 시작 시각은 필수입니다.") Instant startAt,
    @NotNull(message = "이벤트 종료 시각은 필수입니다.") Instant endAt,
    @NotNull(message = "종일 일정 여부는 필수입니다.") Boolean allDay,
    String timeZone,
    Long tagId) {}
