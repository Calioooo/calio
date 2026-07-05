package com.calio.calendar.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomTagRequest(
        @NotBlank(message = "태그 제목은 공백일 수 없습니다.") String title,
        @NotNull(message = "태그 색상 코드는 필수입니다.") String colorCode
) {
}
