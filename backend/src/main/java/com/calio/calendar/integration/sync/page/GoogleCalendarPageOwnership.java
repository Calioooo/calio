package com.calio.calendar.integration.sync.page;

import java.util.Objects;

public record GoogleCalendarPageOwnership(Long jobId, String workerToken) {

    public GoogleCalendarPageOwnership {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(workerToken, "workerToken");
    }
}
