package com.calio.calendar.task.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.task.domain.Task;
import com.calio.calendar.task.repository.TaskRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskQueryService {

    private final TaskRepository taskRepository;

    public TaskQueryService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<Task> listTasks(Long accountId, Pageable pageRequest) {
        return taskRepository.findByAccount_IdAndCompletedFalse(accountId, pageRequest)
                .getContent();
    }

    public Task getTask(Long accountId, Long taskId) {
        return taskRepository.findByTaskIdAndAccount_Id(taskId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.TASK_NOT_FOUND));
    }
}
