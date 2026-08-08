package com.calio.calendar.task.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.task.controller.dto.TaskResponse;
import com.calio.calendar.task.domain.Task;
import com.calio.calendar.task.repository.TaskRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class TaskQueryService {

    private static final int FIRST_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final TaskRepository taskRepository;

    public TaskQueryService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<TaskResponse> listTasks(Long accountId) {
        PageRequest pageRequest = PageRequest.of(
                FIRST_PAGE,
                DEFAULT_PAGE_SIZE,
                Sort.by(Sort.Direction.ASC, "taskId")
        );

        return taskRepository.findByAccount_IdAndCompletedFalse(accountId, pageRequest)
                .getContent()
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    public Task findTask(Long accountId, Long taskId) {
        return taskRepository.findByTaskIdAndAccount_Id(taskId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.TASK_NOT_FOUND));
    }
}
