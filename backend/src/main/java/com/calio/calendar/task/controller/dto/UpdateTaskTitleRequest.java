package com.calio.calendar.task.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateTaskTitleRequest(
        @NotBlank(message = "작업 제목은 공백일 수 없습니다.") String taskTitle
) {
}
