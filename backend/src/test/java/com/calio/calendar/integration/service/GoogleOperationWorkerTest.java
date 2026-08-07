package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleOperationWorkerTest {

    private GoogleOperationProcessor operationProcessor;
    private GoogleOperationWorker worker;

    @BeforeEach
    void setUp() {
        operationProcessor = mock(GoogleOperationProcessor.class);
        worker = new GoogleOperationWorker(operationProcessor);
    }

    @AfterEach
    void tearDown() {
        worker.shutdown();
    }

    @Test
    @DisplayName("처리 중인 Account의 중복 wake는 별도 작업을 시작하지 않는다")
    void givenActiveAccount_whenWokenAgain_thenKeepsSingleExecution() throws Exception {
        // given
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch completeExecution = new CountDownLatch(1);
        doAnswer(invocation -> {
            executionStarted.countDown();
            completeExecution.await(2, TimeUnit.SECONDS);
            return null;
        }).when(operationProcessor).processAccount(10L);

        // when
        worker.wake(10L);
        assertThat(executionStarted.await(2, TimeUnit.SECONDS)).isTrue();
        worker.wake(10L);

        // then
        verify(operationProcessor).processAccount(10L);
        completeExecution.countDown();
        verify(operationProcessor, timeout(2_000L)).processAccount(10L);
    }

    @Test
    @DisplayName("서로 다른 Account 작업은 최대 4개까지만 동시에 실행한다")
    void givenFiveAccounts_whenWoken_thenRunsAtMostFourConcurrently() throws Exception {
        // given
        CountDownLatch firstFourStarted = new CountDownLatch(4);
        CountDownLatch completeExecutions = new CountDownLatch(1);
        AtomicInteger activeExecutions = new AtomicInteger();
        AtomicInteger maximumExecutions = new AtomicInteger();
        doAnswer(invocation -> {
            int currentExecutions = activeExecutions.incrementAndGet();
            maximumExecutions.accumulateAndGet(currentExecutions, Math::max);
            firstFourStarted.countDown();
            try {
                completeExecutions.await(3, TimeUnit.SECONDS);
            } finally {
                activeExecutions.decrementAndGet();
            }
            return null;
        }).when(operationProcessor).processAccount(anyLong());

        // when
        for (long accountId = 1L; accountId <= 5L; accountId++) {
            worker.wake(accountId);
        }
        assertThat(firstFourStarted.await(2, TimeUnit.SECONDS)).isTrue();

        // then
        assertThat(maximumExecutions).hasValue(4);
        completeExecutions.countDown();
        verify(operationProcessor, timeout(2_000L).times(5)).processAccount(anyLong());
    }

}
