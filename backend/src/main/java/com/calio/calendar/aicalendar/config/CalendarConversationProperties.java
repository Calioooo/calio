package com.calio.calendar.aicalendar.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@ConfigurationProperties(prefix = "ai-calendar.conversation")
public class CalendarConversationProperties {

    @NotNull(message = "ai-calendar.conversation.retention must not be null")
    private Duration retention = Duration.ofDays(30);

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }

    @AssertTrue(message = "ai-calendar.conversation.retention must be positive")
    public boolean isRetentionPositive() {
        return retention == null || (!retention.isZero() && !retention.isNegative());
    }
}
