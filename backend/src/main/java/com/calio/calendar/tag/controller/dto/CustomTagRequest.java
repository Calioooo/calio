package com.calio.calendar.tag.controller.dto;

import com.calio.calendar.tag.domain.TagTitle;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomTagRequest(
    @NotBlank(message = "태그 제목은 공백일 수 없습니다.")
        @Size(max = TagTitle.MAX_TAG_TITLE_LENGTH, message = "태그 제목은 최대 20자까지 입력할 수 있습니다.")
        String title,
    @NotBlank(message = "태그 색상 코드는 공백일 수 없습니다.") String colorCode) {}
