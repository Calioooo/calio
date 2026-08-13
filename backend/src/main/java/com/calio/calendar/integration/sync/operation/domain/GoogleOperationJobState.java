package com.calio.calendar.integration.sync.operation.domain;

public enum GoogleOperationJobState {
    PENDING,
    PROCESSING,
    SKIPPED,
    CONFLICTED,
    SYNC_ERROR;
}
