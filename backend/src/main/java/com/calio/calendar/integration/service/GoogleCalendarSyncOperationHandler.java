package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarOperationKind;
import com.calio.calendar.integration.service.GoogleCalendarOperationCoordinator.ClaimedOperation;
import org.springframework.stereotype.Component;

@Component
public class GoogleCalendarSyncOperationHandler implements GoogleCalendarOperationHandler {

    private final GoogleCalendarSyncService syncService;

    public GoogleCalendarSyncOperationHandler(GoogleCalendarSyncService syncService) {
        this.syncService = syncService;
    }

    @Override
    public GoogleCalendarOperationKind kind() {
        return GoogleCalendarOperationKind.SYNC;
    }

    @Override
    public GoogleCalendarOperationResult perform(ClaimedOperation operation) {
        var result = syncService.performOwnedSync(operation.lease());
        return new GoogleCalendarOperationResult(
                result.nextSyncToken(), result.conflictDetected()
        );
    }
}
