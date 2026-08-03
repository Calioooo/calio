package com.calio.calendar.integration.scheduler;

import com.calio.calendar.integration.service.GoogleCalendarOperationDispatcher;
import com.calio.calendar.integration.service.GoogleCalendarOperationEnqueueService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GoogleCalendarOperationScheduler {

    private final GoogleCalendarOperationEnqueueService enqueueService;
    private final GoogleCalendarOperationDispatcher dispatcher;

    public GoogleCalendarOperationScheduler(
            GoogleCalendarOperationEnqueueService enqueueService,
            GoogleCalendarOperationDispatcher dispatcher
    ) {
        this.enqueueService = enqueueService;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelay = 600_000L)
    public void recoverAndSchedulePeriodicSync() {
        enqueueService.connectedAccountIds().forEach(enqueueService::enqueuePeriodicSync);
        dispatcher.dispatchAvailable();
    }
}
