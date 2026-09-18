package com.calio.calendar.task.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.task.controller.dto.TaskResponse;
import com.calio.calendar.task.domain.Task;
import com.calio.calendar.task.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteTaskUseCase {

  private final TaskRepository taskRepository;
  private final Clock clock;

  public CompleteTaskUseCase(TaskRepository taskRepository, Clock clock) {
    this.taskRepository = taskRepository;
    this.clock = clock;
  }

  @Transactional
  public TaskResponse complete(Long accountId, Long taskId) {
    Task task =
        taskRepository
            .findByTaskIdAndAccountId(taskId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.TASK_NOT_FOUND));
    task.changeCompleted(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
    return TaskResponse.from(task);
  }
}
