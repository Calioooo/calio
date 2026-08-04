package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import com.calio.calendar.integration.service.GoogleInboundChangeClassifier.Classification;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarInboundConflictService {

    private final GoogleOperationJobRepository jobRepository;
    private final GoogleInboundChangeClassifier classifier =
            new GoogleInboundChangeClassifier();

    public GoogleCalendarInboundConflictService(GoogleOperationJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public Classification classifyObservation(
            Long accountId,
            Long integrationId,
            GoogleCalendarEffectiveScope scope,
            String baselineHash,
            String canonicalHash,
            String googleHash
    ) {
        return classifier.classify(
                baselineHash,
                canonicalHash,
                pendingHashes(accountId, integrationId, scope),
                googleHash
        );
    }

    public Classification classifyDeletion(
            Long accountId,
            Long integrationId,
            GoogleCalendarEffectiveScope scope,
            String baselineHash,
            String canonicalHash
    ) {
        return classifier.classifyDeletion(
                baselineHash,
                canonicalHash,
                pendingHashes(accountId, integrationId, scope)
        );
    }

    public Classification classifyRecurrenceEventDeletion(
            Long accountId,
            Long integrationId,
            GoogleCalendarEffectiveScope.RecurrenceMaster scope,
            String baselineHash,
            String canonicalHash
    ) {
        return classifier.classifyDeletion(
                baselineHash,
                canonicalHash,
                hasPendingRecurrenceBranch(accountId, integrationId, scope)
        );
    }

    private List<String> pendingHashes(
            Long accountId,
            Long integrationId,
            GoogleCalendarEffectiveScope scope
    ) {
        return jobRepository.findPendingDesiredContentHashes(
                accountId,
                integrationId,
                scope.encodedType(),
                scope.encodedKey()
        );
    }

    private boolean hasPendingRecurrenceBranch(
            Long accountId,
            Long integrationId,
            GoogleCalendarEffectiveScope.RecurrenceMaster scope
    ) {
        String childPrefix = GoogleCalendarEffectiveScope.recurrenceOverrideKeyPrefix(
                scope.recurrenceEventId()
        );
        return jobRepository.countPendingRecurrenceAggregateBranches(
                accountId,
                integrationId,
                scope.encodedKey(),
                childPrefix,
                childPrefix.length()
        ) > 0;
    }
}
