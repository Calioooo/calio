package com.calio.calendar.task.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.task.controller.dto.CreateTaskRequest;
import com.calio.calendar.task.controller.dto.TaskResponse;
import com.calio.calendar.task.controller.dto.UpdateTaskTitleRequest;
import com.calio.calendar.task.domain.Task;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private static final int FIRST_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final TaskQueryService taskQueryService;
    private final TaskCommandService taskCommandService;
    private final AccountRepository accountRepository;
    private final Clock clock;

    public TaskService(
            TaskQueryService taskQueryService,
            TaskCommandService taskCommandService,
            AccountRepository accountRepository,
            Clock clock
    ) {
        this.taskQueryService = taskQueryService;
        this.taskCommandService = taskCommandService;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse createTask(Long accountId, CreateTaskRequest request) {
        Account account = accountRepository.getReferenceById(accountId);
        Task task = taskCommandService.createTask(request.toEntity(account));
        return TaskResponse.from(task);
    }

    public List<TaskResponse> listTasks(Long accountId) {
        PageRequest pageRequest = PageRequest.of(
                FIRST_PAGE,
                DEFAULT_PAGE_SIZE,
                Sort.by(Sort.Direction.ASC, "taskId")
        );
        return taskQueryService.listTasks(accountId, pageRequest)
                .stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse completeTask(Long accountId, Long taskId) {
        Task task = taskQueryService.findTask(accountId, taskId);
        taskCommandService.completeTask(task, currentPersistenceTime());
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse uncompleteTask(Long accountId, Long taskId) {
        Task task = taskQueryService.findTask(accountId, taskId);
        taskCommandService.uncompleteTask(task);
        return TaskResponse.from(task);
    }

    @Transactional
    public TaskResponse updateTaskTitle(Long accountId, Long taskId, UpdateTaskTitleRequest request) {
        Task task = taskQueryService.findTask(accountId, taskId);
        if (task.isCompleted()) {
            throw new CalioException(ErrorCode.COMPLETED_TASK_TITLE_UPDATE_NOT_ALLOWED);
        }

        taskCommandService.updateTaskTitle(task, request.taskTitle());
        return TaskResponse.from(task);
    }

    @Transactional
    public int deleteCompletedTasksBefore(Instant cutoff) {
        return taskCommandService.deleteCompletedTasksBefore(cutoff);
    }

    private Instant currentPersistenceTime() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
