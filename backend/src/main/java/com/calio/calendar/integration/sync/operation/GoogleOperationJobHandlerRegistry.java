package com.calio.calendar.integration.sync.operation;

import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJob;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class GoogleOperationJobHandlerRegistry {

    private final Map<Class<? extends GoogleOperationJob>, GoogleOperationJobHandler> handlers;

    public GoogleOperationJobHandlerRegistry(List<GoogleOperationJobHandler> handlers) {
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(
                GoogleOperationJobHandler::jobType,
                Function.identity(),
                (first, second) -> {
                    throw new IllegalStateException(
                            "Multiple Google operation job handlers for " + first.jobType().getName());
                }
        ));
    }

    public void execute(GoogleOperationJob job, String workerToken) {
        GoogleOperationJobHandler handler = handlers.get(job.getClass());
        if (handler == null) {
            throw new GoogleOperationJobHandlerNotFoundException(job.getClass());
        }
        handler.execute(job, workerToken);
    }
}
