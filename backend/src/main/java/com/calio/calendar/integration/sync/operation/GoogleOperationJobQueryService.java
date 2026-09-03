package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScopeType;
import com.calio.calendar.integration.sync.operation.repository.GoogleOperationJobRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoogleOperationJobQueryService {

    private final GoogleOperationJobRepository jobRepository;

    public GoogleOperationJobQueryService(GoogleOperationJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public List<Long> listRecoverableAccountIds(Instant now, int limit) {
        return jobRepository.findRecoverableAccountIds(now, PageRequest.of(0, limit));
    }

    public List<Long> listExpiredTerminalJobIds(Instant cutoff, int limit) {
        return jobRepository.findTerminalIdsBefore(cutoff, PageRequest.of(0, limit));
    }

    public boolean hasPendingOutboundJob(
            Long accountId,
            Long connectionId,
            GoogleCalendarEffectiveScope scope
    ) {
        if (scope.isRecurrenceEventAggregate()) {
            return jobRepository.existsPendingOutboundJobForRecurrenceAggregate(
                    accountId,
                    connectionId,
                    GoogleCalendarEffectiveScopeType.RECURRENCE_EVENT.getStoredValue(),
                    scope.storedKey(),
                    GoogleCalendarEffectiveScopeType.RECURRENCE_OVERRIDE.getStoredValue(),
                    scope.childOverrideKeyPrefix()
            );
        }
        return jobRepository.existsPendingOutboundJob(
                accountId,
                connectionId,
                scope.storedScope(),
                scope.storedKey()
        );
    }
}
