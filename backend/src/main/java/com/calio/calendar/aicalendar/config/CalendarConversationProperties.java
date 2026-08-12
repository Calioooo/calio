package com.calio.calendar.aicalendar.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai-calendar.conversation")
public class CalendarConversationProperties {

    private Duration retention = Duration.ofDays(30);

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }
}
