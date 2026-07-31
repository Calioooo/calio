package com.calio.calendar.groupinvitation.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@ConfigurationProperties(prefix = "group-invitation")
public class GroupInvitationProperties {

    private static final int MAX_CLEANUP_BATCHES_PER_RUN = 1000;

    @NotBlank(message = "group-invitation.base-url must not be blank")
    private String baseUrl = "https://calio.app/invite";

    @NotNull(message = "group-invitation.ttl must not be null")
    private Duration ttl = Duration.ofHours(24);

    @NotNull(message = "group-invitation.cleanup-fixed-delay must not be null")
    private Duration cleanupFixedDelay = Duration.ofHours(1);

    @NotNull(message = "group-invitation.expired-retention must not be null")
    private Duration expiredRetention = Duration.ofHours(24);

    @Min(value = 1, message = "group-invitation.cleanup-batch-size must be at least 1")
    @Max(value = 1000, message = "group-invitation.cleanup-batch-size must not exceed 1000")
    private int cleanupBatchSize = 1000;

    @Min(value = 1, message = "group-invitation.cleanup-max-batches-per-run must be at least 1")
    @Max(
            value = MAX_CLEANUP_BATCHES_PER_RUN,
            message = "group-invitation.cleanup-max-batches-per-run must not exceed 1000"
    )
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

    @AssertTrue(message = "group-invitation.ttl must be positive")
    public boolean isTtlPositive() {
        return ttl == null || isPositive(ttl);
    }

    @AssertTrue(message = "group-invitation.cleanup-fixed-delay must be positive")
    public boolean isCleanupFixedDelayPositive() {
        return cleanupFixedDelay == null || isPositive(cleanupFixedDelay);
    }

    @AssertTrue(message = "group-invitation.expired-retention must be positive")
    public boolean isExpiredRetentionPositive() {
        return expiredRetention == null || isPositive(expiredRetention);
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
