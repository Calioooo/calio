package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CreateTaskRequest;
import com.calio.calendar.controller.dto.TaskResponse;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.TaskRepository;
import com.calio.calendar.repository.entity.Task;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        Task task = taskRepository.save(request.toEntity());
        return TaskResponse.from(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks() {
        return taskRepository.findAllByOrderByTaskIdAsc()
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(Long taskId) {
        Task task = findTask(taskId);
        return TaskResponse.from(task);
    }

    private Task findTask(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new CalioException(ErrorCode.TASK_NOT_FOUND));
    }
}
