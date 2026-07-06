package com.calio.calendar.scheduler;

import com.calio.calendar.service.TaskService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TaskCleanupScheduler {

    static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final Logger log = LoggerFactory.getLogger(TaskCleanupScheduler.class);
    private static final int COMPLETED_TASK_RETENTION_DAYS = 30;

    private final TaskService taskService;
    private final Clock clock;

    @Autowired
    public TaskCleanupScheduler(TaskService taskService) {
        this(taskService, Clock.system(KOREA_ZONE));
    }

    TaskCleanupScheduler(TaskService taskService, Clock clock) {
        this.taskService = taskService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void deleteOldCompletedTasks() {
        try {
            Instant cutoff = Instant.now(clock).minus(COMPLETED_TASK_RETENTION_DAYS, ChronoUnit.DAYS);
            int deletedCount = taskService.deleteCompletedTasksOlderThan(cutoff);
            log.info("Completed task cleanup finished. cutoff={} deletedCount={}", cutoff, deletedCount);
        } catch (Exception exception) {
            log.error("Completed task cleanup failed. message={}", exception.getMessage(), exception);
        }
    }
}
