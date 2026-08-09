package com.calio.calendar.integration.sync.operation.domain;

public enum GoogleOperationJobState {
    PENDING,
    PROCESSING,
    SKIPPED,
    CONFLICTED,
    SYNC_ERROR;

    public boolean isTerminal() {
        return this == SKIPPED || this == CONFLICTED || this == SYNC_ERROR;
    }
}
