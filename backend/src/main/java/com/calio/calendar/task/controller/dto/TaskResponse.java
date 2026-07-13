package com.calio.calendar.task.controller.dto;

import com.calio.calendar.task.domain.Task;
import java.time.Instant;

public record TaskResponse(
        Long taskId,
        String taskTitle,
        boolean isCompleted,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getTaskId(),
                task.getTaskTitle(),
                task.isCompleted(),
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
