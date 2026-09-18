package com.calio.calendar.task.usecase;

import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.task.controller.dto.TaskResponse;
import com.calio.calendar.task.domain.Task;
import com.calio.calendar.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateTaskUseCase {

  private final AccountRepository accountRepository;
  private final TaskRepository taskRepository;

  public CreateTaskUseCase(AccountRepository accountRepository, TaskRepository taskRepository) {
    this.accountRepository = accountRepository;
    this.taskRepository = taskRepository;
  }

  @Transactional
  public TaskResponse create(Long accountId, String taskTitle) {
    accountRepository
        .findById(accountId)
        .orElseThrow(() -> new CalioException(ErrorCode.INTERNAL_SERVER_ERROR));
    Task task = taskRepository.save(new Task(taskTitle, accountId));
    return TaskResponse.from(task);
  }
}
