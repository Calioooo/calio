package com.calio.calendar.integration.sync;

import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJob;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarEventDeleteJobService {

    private final GoogleCalendarEventJobSupport jobSupport;

    public GoogleCalendarEventDeleteJobService(GoogleCalendarEventJobSupport jobSupport) {
        this.jobSupport = jobSupport;
    }

    public void execute(GoogleCalendarEventJob job, String workerToken) {
        List<EventMappingSnapshot> mappings = jobSupport.loadMappingSnapshots(job);
        List<MappingExecutionResult> mappingResults = jobSupport.deleteMappedEvents(mappings);
        jobSupport.complete(job, workerToken, mappingResults);
    }
}
