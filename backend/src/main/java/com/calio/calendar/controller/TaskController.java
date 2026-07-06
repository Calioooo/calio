package com.calio.calendar.controller;

import com.calio.calendar.controller.dto.CreateTaskRequest;
import com.calio.calendar.controller.dto.TaskResponse;
import com.calio.calendar.service.TaskService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse response = taskService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<TaskResponse> listTasks() {
        return taskService.listTasks();
    }

    @DeleteMapping("/{taskId}")
    public TaskResponse completeTask(@PathVariable Long taskId) {
        return taskService.completeTask(taskId);
    }

    @PatchMapping("/{taskId}/uncomplete")
    public TaskResponse uncompleteTask(@PathVariable Long taskId) {
        return taskService.uncompleteTask(taskId);
    }
}
