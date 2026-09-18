package com.calio.calendar.task.usecase;

import com.calio.calendar.task.controller.dto.TaskResponse;
import com.calio.calendar.task.repository.TaskRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListTasksUseCase {

  private static final int DEFAULT_PAGE_SIZE = 20;

  private final TaskRepository taskRepository;

  public ListTasksUseCase(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  @Transactional(readOnly = true)
  public List<TaskResponse> list(Long accountId) {
    PageRequest pageRequest =
        PageRequest.of(0, DEFAULT_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "taskId"));
    return taskRepository
        .findByAccountIdAndStateCompletedFalse(accountId, pageRequest)
        .map(TaskResponse::from)
        .getContent();
  }
}
