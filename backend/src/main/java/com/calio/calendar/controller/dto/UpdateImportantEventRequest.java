package com.calio.calendar.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateImportantEventRequest(
        @JsonProperty("isImportantEvent")
        @NotNull(message = "중요 일정 여부는 필수입니다.")
        Boolean isImportantEvent
) {
}
