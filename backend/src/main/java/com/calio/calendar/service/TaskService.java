package com.calio.calendar.service;

import com.calio.calendar.controller.dto.CreateTaskRequest;
import com.calio.calendar.controller.dto.TaskResponse;
import com.calio.calendar.controller.dto.UpdateTaskTitleRequest;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.AccountRepository;
import com.calio.calendar.repository.TaskRepository;
import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.Task;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final AccountRepository accountRepository;

    public TaskService(TaskRepository taskRepository, AccountRepository accountRepository) {
        this.taskRepository = taskRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public TaskResponse createTask(Long accountId, CreateTaskRequest request) {
        Account account = accountRepository.getReferenceById(accountId);
        Task task = taskRepository.save(request.toEntity(account));
        return TaskResponse.from(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listTasks(Long accountId) {
        PageRequest pageRequest = PageRequest.of(
                FIRST_PAGE,
                DEFAULT_PAGE_SIZE,
                Sort.by(Sort.Direction.ASC, "taskId")
        );

        return taskRepository.findByCompletedFalseAndAccount_Id(accountId, pageRequest)
                .getContent()
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse completeTask(Long accountId, Long taskId) {
        Task task = getTask(accountId, taskId);
        task.complete(currentPersistenceTime());
        taskRepository.flush();
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse uncompleteTask(Long accountId, Long taskId) {
        Task task = getTask(accountId, taskId);
        task.uncomplete();
        taskRepository.flush();
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse updateTaskTitle(Long accountId, Long taskId, UpdateTaskTitleRequest request) {
        Task task = getTask(accountId, taskId);
        if (task.isCompleted()) {
            throw new CalioException(ErrorCode.COMPLETED_TASK_TITLE_UPDATE_NOT_ALLOWED);
        }

        task.updateTitle(request.taskTitle());
        taskRepository.flush();
        return TaskResponse.from(task);
    }

    @Transactional
    public int deleteCompletedTasksOlderThan(Instant cutoff) {
        return taskRepository.deleteCompletedTasksOlderThan(cutoff);
    }

    private Task getTask(Long accountId, Long taskId) {
        return taskRepository.findByTaskIdAndAccount_Id(taskId, accountId)
                .orElseThrow(() -> new CalioException(ErrorCode.TASK_NOT_FOUND));
    }

    private Instant currentPersistenceTime() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
