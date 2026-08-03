package com.calio.calendar.integration.service;

import com.calio.calendar.integration.domain.GoogleCalendarOperationKind;
import com.calio.calendar.integration.service.GoogleCalendarOperationCoordinator.ClaimedOperation;

public interface GoogleCalendarOperationHandler {
    GoogleCalendarOperationKind kind();

    GoogleCalendarOperationResult perform(ClaimedOperation operation);
}
