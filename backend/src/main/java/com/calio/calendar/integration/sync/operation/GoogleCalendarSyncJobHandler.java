package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.sync.GoogleCalendarSyncService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarSyncJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarSyncJobHandler implements GoogleOperationJobHandler {

    private final GoogleCalendarSyncService syncService;

    public GoogleCalendarSyncJobHandler(GoogleCalendarSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public Class<GoogleCalendarSyncJob> jobType() {
        return GoogleCalendarSyncJob.class;
    }

    @Override
    public void execute(GoogleOperationJob job, String workerToken) {
        GoogleCalendarSyncJob syncJob = (GoogleCalendarSyncJob) job;
        syncService.synchronize(syncJob.getId(), syncJob.getAccountId(), workerToken);
    }
}
