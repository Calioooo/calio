package com.calio.calendar.integration.sync.operation;

import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GoogleOperationWorker {

    private static final Logger log = LoggerFactory.getLogger(GoogleOperationWorker.class);
    private static final long SHUTDOWN_GRACE_SECONDS = 5L;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Set<Long> activeAccounts = ConcurrentHashMap.newKeySet();
    private final GoogleOperationProcessor operationProcessor;

    public GoogleOperationWorker(GoogleOperationProcessor operationProcessor) {
        this.operationProcessor = operationProcessor;
    }

    public void wake(Long accountId) {
        if (!activeAccounts.add(accountId)) {
            return;
        }
        try {
            executor.execute(() -> processAccount(accountId));
        } catch (RejectedExecutionException exception) {
            activeAccounts.remove(accountId);
            log.warn("Google operation wake-up was rejected. accountId={}", accountId);
        }
    }

    private void processAccount(Long accountId) {
        try {
            operationProcessor.processAccount(accountId);
        } catch (RuntimeException exception) {
            log.warn("Google operation worker failed. accountId={} causeType={}",
                    accountId, exception.getClass().getSimpleName());
        } finally {
            activeAccounts.remove(accountId);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_GRACE_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
