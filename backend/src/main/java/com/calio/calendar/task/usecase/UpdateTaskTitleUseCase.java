package com.calio.calendar.task.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.task.controller.dto.TaskResponse;
import com.calio.calendar.task.domain.Task;
import com.calio.calendar.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateTaskTitleUseCase {

  private final TaskRepository taskRepository;

  public UpdateTaskTitleUseCase(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  @Transactional
  public TaskResponse update(Long accountId, Long taskId, String taskTitle) {
    Task task =
        taskRepository
            .findByTaskIdAndAccountId(taskId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.TASK_NOT_FOUND));
    task.updateTitle(taskTitle);
    return TaskResponse.from(task);
  }
}
