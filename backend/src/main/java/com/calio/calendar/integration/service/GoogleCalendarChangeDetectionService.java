package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarSyncTarget;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import com.calio.calendar.integration.service.GoogleCalendarChangeDetector.ChangeType;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarChangeDetectionService {

    private final GoogleOperationJobRepository jobRepository;
    private final GoogleCalendarChangeDetector changeDetector =
            new GoogleCalendarChangeDetector();

    public GoogleCalendarChangeDetectionService(GoogleOperationJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public ChangeType detectUpdate(
            Long accountId,
            Long integrationId,
            GoogleCalendarSyncTarget syncTarget,
            String lastSyncedContentHash,
            String currentCalioContentHash,
            String currentGoogleContentHash
    ) {
        return changeDetector.detectUpdate(
                lastSyncedContentHash,
                currentCalioContentHash,
                findPendingGoogleWriteHashes(accountId, integrationId, syncTarget),
                currentGoogleContentHash
        );
    }

    public ChangeType detectDeletion(
            Long accountId,
            Long integrationId,
            GoogleCalendarSyncTarget syncTarget,
            String lastSyncedContentHash,
            String currentCalioContentHash
    ) {
        return changeDetector.detectDeletion(
                lastSyncedContentHash,
                currentCalioContentHash,
                findPendingGoogleWriteHashes(accountId, integrationId, syncTarget)
        );
    }

    public ChangeType detectRecurrenceEventDeletion(
            Long accountId,
            Long integrationId,
            GoogleCalendarSyncTarget.RecurrenceEvent syncTarget,
            String lastSyncedContentHash,
            String currentCalioContentHash
    ) {
        return changeDetector.detectDeletion(
                lastSyncedContentHash,
                currentCalioContentHash,
                hasPendingRecurrenceChange(accountId, integrationId, syncTarget)
        );
    }

    private List<String> findPendingGoogleWriteHashes(
            Long accountId,
            Long integrationId,
            GoogleCalendarSyncTarget syncTarget
    ) {
        return jobRepository.findPendingGoogleWriteContentHashes(
                accountId,
                integrationId,
                syncTarget.storedType(),
                syncTarget.storedKey()
        );
    }

    private boolean hasPendingRecurrenceChange(
            Long accountId,
            Long integrationId,
            GoogleCalendarSyncTarget.RecurrenceEvent syncTarget
    ) {
        String overrideKeyPrefix = GoogleCalendarSyncTarget.overrideKeyPrefix(
                syncTarget.recurrenceEventId()
        );
        return jobRepository.countPendingRecurrenceChanges(
                accountId,
                integrationId,
                syncTarget.storedKey(),
                overrideKeyPrefix,
                overrideKeyPrefix.length()
        ) > 0;
    }
}
