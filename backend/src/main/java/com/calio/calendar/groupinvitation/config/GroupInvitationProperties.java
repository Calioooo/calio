package com.calio.calendar.groupinvitation.config;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "group-invitation")
public class GroupInvitationProperties {

    private static final int MAX_CLEANUP_BATCHES_PER_RUN = 1000;

    private String baseUrl = "https://calio.app/invite";
    private Duration ttl = Duration.ofHours(24);
    private Duration cleanupFixedDelay = Duration.ofHours(1);
    private Duration expiredRetention = Duration.ofHours(24);
    private int cleanupBatchSize = 1000;
    private int cleanupMaxBatchesPerRun = 100;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getCleanupFixedDelay() {
        return cleanupFixedDelay;
    }

    public void setCleanupFixedDelay(Duration cleanupFixedDelay) {
        this.cleanupFixedDelay = cleanupFixedDelay;
    }

    public Duration getExpiredRetention() {
        return expiredRetention;
    }

    public void setExpiredRetention(Duration expiredRetention) {
        this.expiredRetention = expiredRetention;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public int getCleanupMaxBatchesPerRun() {
        return cleanupMaxBatchesPerRun;
    }

    public void setCleanupMaxBatchesPerRun(int cleanupMaxBatchesPerRun) {
        this.cleanupMaxBatchesPerRun = cleanupMaxBatchesPerRun;
    }

    @PostConstruct
    void validate() {
        if (baseUrl == null || baseUrl.isBlank()
                || !isPositive(ttl)
                || !isPositive(cleanupFixedDelay)
                || !isPositive(expiredRetention)
                || cleanupBatchSize < 1
                || cleanupBatchSize > 1000
                || cleanupMaxBatchesPerRun < 1
                || cleanupMaxBatchesPerRun > MAX_CLEANUP_BATCHES_PER_RUN) {
            throw new IllegalStateException("Invalid group invitation configuration.");
        }
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
