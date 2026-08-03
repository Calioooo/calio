package com.calio.calendar.integration.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleCalendarOperationExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService googleCalendarOperationExecutor() {
        return Executors.newFixedThreadPool(4, Thread.ofPlatform()
                .name("google-calendar-operation-", 0)
                .factory());
    }
}
