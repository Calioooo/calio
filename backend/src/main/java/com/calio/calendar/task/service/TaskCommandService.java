package com.calio.calendar.task.service;

import com.calio.calendar.task.domain.Task;
import com.calio.calendar.task.repository.TaskRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class TaskCommandService {

    private final TaskRepository taskRepository;

    public TaskCommandService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Task createTask(Task task) {
        return taskRepository.save(task);
    }

    public void completeTask(Task task, Instant completedAt) {
        task.changeCompleted(completedAt);
        taskRepository.flush();
    }

    public void uncompleteTask(Task task) {
        task.changeUncompleted();
        taskRepository.flush();
    }

    public void updateTaskTitle(Task task, String taskTitle) {
        task.updateTitle(taskTitle);
        taskRepository.flush();
    }

    public int deleteCompletedTasksBefore(Instant cutoff) {
        return taskRepository.deleteCompletedTasksBefore(cutoff);
    }
}
