package com.calio.calendar.task.controller;

import com.calio.calendar.security.AuthenticatedAccount;
import com.calio.calendar.task.controller.dto.CreateTaskRequest;
import com.calio.calendar.task.controller.dto.TaskResponse;
import com.calio.calendar.task.controller.dto.UpdateTaskTitleRequest;
import com.calio.calendar.task.usecase.CompleteTaskUseCase;
import com.calio.calendar.task.usecase.CreateTaskUseCase;
import com.calio.calendar.task.usecase.ListTasksUseCase;
import com.calio.calendar.task.usecase.UncompleteTaskUseCase;
import com.calio.calendar.task.usecase.UpdateTaskTitleUseCase;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

  private final CreateTaskUseCase createTaskUseCase;
  private final ListTasksUseCase listTasksUseCase;
  private final CompleteTaskUseCase completeTaskUseCase;
  private final UncompleteTaskUseCase uncompleteTaskUseCase;
  private final UpdateTaskTitleUseCase updateTaskTitleUseCase;

  public TaskController(
      CreateTaskUseCase createTaskUseCase,
      ListTasksUseCase listTasksUseCase,
      CompleteTaskUseCase completeTaskUseCase,
      UncompleteTaskUseCase uncompleteTaskUseCase,
      UpdateTaskTitleUseCase updateTaskTitleUseCase) {
    this.createTaskUseCase = createTaskUseCase;
    this.listTasksUseCase = listTasksUseCase;
    this.completeTaskUseCase = completeTaskUseCase;
    this.uncompleteTaskUseCase = uncompleteTaskUseCase;
    this.updateTaskTitleUseCase = updateTaskTitleUseCase;
  }

  @PostMapping
  public ResponseEntity<TaskResponse> createTask(
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody CreateTaskRequest request) {
    TaskResponse response = createTaskUseCase.create(account.accountId(), request.taskTitle());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping
  public List<TaskResponse> listTasks(@AuthenticationPrincipal AuthenticatedAccount account) {
    return listTasksUseCase.list(account.accountId());
  }

  @DeleteMapping("/{taskId}")
  public TaskResponse completeTask(
      @AuthenticationPrincipal AuthenticatedAccount account, @PathVariable("taskId") Long taskId) {
    return completeTaskUseCase.complete(account.accountId(), taskId);
  }

  @PatchMapping("/{taskId}/uncomplete")
  public TaskResponse uncompleteTask(
      @AuthenticationPrincipal AuthenticatedAccount account, @PathVariable("taskId") Long taskId) {
    return uncompleteTaskUseCase.uncomplete(account.accountId(), taskId);
  }

  @PatchMapping("/{taskId}")
  public TaskResponse updateTaskTitle(
      @PathVariable("taskId") Long taskId,
      @AuthenticationPrincipal AuthenticatedAccount account,
      @Valid @RequestBody UpdateTaskTitleRequest request) {
    return updateTaskTitleUseCase.update(account.accountId(), taskId, request.taskTitle());
  }
}
