package com.calio.calendar.singleevent.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateImportantSingleEventRequest(
        @NotNull(message = "중요 일정 여부는 필수입니다.")
        Boolean importantEvent
) {
}
