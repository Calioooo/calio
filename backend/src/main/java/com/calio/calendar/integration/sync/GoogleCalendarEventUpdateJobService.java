package com.calio.calendar.integration.sync;

import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJob;
import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarEventUpdateJobService {

    private final GoogleCalendarEventJobSupport jobSupport;

    public GoogleCalendarEventUpdateJobService(GoogleCalendarEventJobSupport jobSupport) {
        this.jobSupport = jobSupport;
    }

    public void execute(GoogleCalendarEventJob job, String workerToken) {
        GoogleEventJobPayload eventSnapshot = jobSupport.readEventSnapshot(job);
        List<EventMappingSnapshot> mappings = jobSupport.loadMappingSnapshots(job);
        List<MappingExecutionResult> mappingResults = jobSupport.patchMappedEvents(eventSnapshot, mappings);
        jobSupport.complete(job, workerToken, mappingResults);
    }
}
