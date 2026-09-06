package com.calio.calendar.integration.sync;

import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJob;
import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarEventCreateJobService {

    private final GoogleCalendarEventJobSupport jobSupport;

    public GoogleCalendarEventCreateJobService(GoogleCalendarEventJobSupport jobSupport) {
        this.jobSupport = jobSupport;
    }

    public void execute(GoogleCalendarEventJob job, String workerToken) {
        GoogleEventJobPayload eventSnapshot = jobSupport.readEventSnapshot(job);
        List<EventMappingSnapshot> mappings = jobSupport.loadMappingSnapshots(job);
        Long targetConnectionId = jobSupport.findCreationTarget(job, mappings);
        List<MappingExecutionResult> mappingResults = jobSupport.patchMappedEvents(eventSnapshot, mappings);
        GoogleCalendarEventResponse createdEvent = null;
        if (mappingOutcome(mappingResults) == MappingOutcome.APPLIED && targetConnectionId != null) {
            createdEvent = jobSupport.insertEvent(job, eventSnapshot, targetConnectionId);
        }
        jobSupport.completeCreate(job, workerToken, mappingResults, targetConnectionId, createdEvent);
    }

    private MappingOutcome mappingOutcome(List<MappingExecutionResult> mappingResults) {
        return mappingResults.stream()
                .map(MappingExecutionResult::outcome)
                .reduce(MappingOutcome.APPLIED, MappingOutcome::merge);
    }
}
