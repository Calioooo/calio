package com.calio.calendar.integration.scheduler;

import com.calio.calendar.integration.repository.GoogleCalendarOperationJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GoogleCalendarOperationCleanupScheduler {

    private static final Duration RETENTION = Duration.ofDays(30);
    private static final int BATCH_SIZE = 500;

    private final GoogleCalendarOperationJobRepository jobRepository;
    private final Clock clock;

    public GoogleCalendarOperationCleanupScheduler(
            GoogleCalendarOperationJobRepository jobRepository,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "0 20 4 * * *", zone = "UTC")
    @Transactional
    public void deleteExpiredDiagnosticJobs() {
        Instant cutoff = Instant.now(clock).minus(RETENTION);
        deleteNextBatch(cutoff);
    }

    private int deleteNextBatch(Instant cutoff) {
        List<Long> ids = jobRepository.findTerminalIdsBefore(
                cutoff,
                PageRequest.of(0, BATCH_SIZE)
        );
        if (ids.isEmpty()) {
            return 0;
        }
        jobRepository.deleteAllByIds(ids);
        return ids.size();
    }
}
