package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CreateTaskRequest;
import com.calio.calendar.controller.dto.TaskResponse;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.TaskRepository;
import com.calio.calendar.repository.entity.Task;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final int FIRST_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;

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
        PageRequest pageRequest = PageRequest.of(
                FIRST_PAGE,
                DEFAULT_PAGE_SIZE,
                Sort.by(Sort.Direction.ASC, "taskId")
        );

        return taskRepository.findAll(pageRequest)
                .getContent()
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
