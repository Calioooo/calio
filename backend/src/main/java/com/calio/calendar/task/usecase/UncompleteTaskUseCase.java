package com.calio.calendar.task.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.task.controller.dto.TaskResponse;
import com.calio.calendar.task.domain.Task;
import com.calio.calendar.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UncompleteTaskUseCase {

  private final TaskRepository taskRepository;

  public UncompleteTaskUseCase(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  @Transactional
  public TaskResponse uncomplete(Long accountId, Long taskId) {
    Task task =
        taskRepository
            .findByTaskIdAndAccountId(taskId, accountId)
            .orElseThrow(() -> new CalioException(ErrorCode.TASK_NOT_FOUND));
    task.changeUncompleted();
    return TaskResponse.from(task);
  }
}
