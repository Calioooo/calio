package com.calio.calendar.integration.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GoogleCalendarOperationDispatcher implements GoogleCalendarOperationWakeup {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarOperationDispatcher.class);
    private static final int MAX_CONCURRENT_ACCOUNTS = 4;

    private final GoogleCalendarOperationCoordinator coordinator;
    private final GoogleCalendarOperationWorker worker;
    private final ExecutorService executor;
    private final Set<Long> activeAccounts = ConcurrentHashMap.newKeySet();

    public GoogleCalendarOperationDispatcher(
            GoogleCalendarOperationCoordinator coordinator,
            GoogleCalendarOperationWorker worker,
            ExecutorService googleCalendarOperationExecutor
    ) {
        this.coordinator = coordinator;
        this.worker = worker;
        this.executor = googleCalendarOperationExecutor;
    }

    @Override
    public void wakeUp() {
        dispatchAvailable();
    }

    public synchronized void dispatchAvailable() {
        int capacity = MAX_CONCURRENT_ACCOUNTS - activeAccounts.size();
        if (capacity <= 0) {
            return;
        }
        coordinator.findRunnableAccountIds(capacity * 2).stream()
                .filter(activeAccounts::add)
                .limit(capacity)
                .forEach(this::submit);
    }

    private void submit(Long accountId) {
        try {
            executor.submit(() -> runAccount(accountId));
        } catch (RejectedExecutionException rejected) {
            activeAccounts.remove(accountId);
            log.info("Google operation executor rejected dispatch during shutdown");
        }
    }

    private void runAccount(Long accountId) {
        try {
            worker.processAccount(accountId);
        } finally {
            activeAccounts.remove(accountId);
            dispatchAvailable();
        }
    }
}
