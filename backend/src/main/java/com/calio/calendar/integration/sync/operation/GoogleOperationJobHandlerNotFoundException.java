package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;

public class GoogleOperationJobHandlerNotFoundException extends RuntimeException {

    public GoogleOperationJobHandlerNotFoundException(Class<? extends GoogleOperationJob> jobType) {
        super("No Google operation job handler for " + jobType.getName());
    }
}
