package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;

public interface GoogleOperationJobHandler {

    Class<? extends GoogleOperationJob> jobType();

    void execute(GoogleOperationJob job, String workerToken);
}
