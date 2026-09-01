package com.calio.calendar.aicalendar.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai-calendar.agent")
public class CalendarAIProperties {

    private Duration runTimeout = Duration.ofSeconds(30);
    private Duration connectionTimeout = Duration.ofSeconds(5);
    private int maxToolCalls = 2;
    private int maximumQueryDays = 14;
    private Duration conversationRetention = Duration.ofDays(30);

    public Duration getRunTimeout() {
        return runTimeout;
    }

    public void setRunTimeout(Duration runTimeout) {
        this.runTimeout = runTimeout;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public int getMaximumQueryDays() {
        return maximumQueryDays;
    }

    public void setMaximumQueryDays(int maximumQueryDays) {
        this.maximumQueryDays = maximumQueryDays;
    }

    public Duration getConversationRetention() {
        return conversationRetention;
    }

    public void setConversationRetention(Duration conversationRetention) {
        this.conversationRetention = conversationRetention;
    }
}
