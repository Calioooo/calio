package com.calio.calendar.task.usecase;

import com.calio.calendar.task.repository.TaskRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteCompletedTasksUseCase {

  private final TaskRepository taskRepository;

  public DeleteCompletedTasksUseCase(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  @Transactional
  public int deleteBefore(Instant cutoff) {
    return taskRepository.deleteCompletedTasksBefore(cutoff);
  }
}
