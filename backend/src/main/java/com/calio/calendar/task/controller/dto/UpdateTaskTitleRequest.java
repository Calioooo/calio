package com.calio.calendar.task.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTaskTitleRequest(
    @NotBlank(message = "작업 제목은 공백일 수 없습니다.") @Size(max = 40, message = "작업 제목은 40자 이하여야 합니다.")
        String taskTitle) {}
