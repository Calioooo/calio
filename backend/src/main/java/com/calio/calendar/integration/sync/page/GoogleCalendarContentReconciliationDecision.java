package com.calio.calendar.integration.sync.page;

public enum GoogleCalendarContentReconciliationDecision {
    GOOGLE_ONLY,
    CALIO_ONLY,
    ALREADY_CONVERGED,
    METADATA_ONLY,
    TRUE_CONFLICT
}
