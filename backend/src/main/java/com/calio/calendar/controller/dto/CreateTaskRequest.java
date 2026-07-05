package com.calio.calendar.controller.dto;

import com.calio.calendar.repository.entity.Task;
import jakarta.validation.constraints.NotBlank;

public record CreateTaskRequest(
        @NotBlank(message = "작업 제목은 공백일 수 없습니다.") String taskTitle
) {

    public Task toEntity() {
        return new Task(taskTitle);
    }
}
