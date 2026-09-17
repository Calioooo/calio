package com.calio.calendar.aicalendar.scheduler;

import com.calio.calendar.aicalendar.config.CalendarConversationProperties;
import com.calio.calendar.aicalendar.service.CalendarConversationService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CalendarConversationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CalendarConversationCleanupScheduler.class);

    private final CalendarConversationService conversationService;
    private final CalendarConversationProperties properties;
    private final Clock clock;

    public CalendarConversationCleanupScheduler(
            CalendarConversationService conversationService,
            CalendarConversationProperties properties,
            Clock clock
    ) {
        this.conversationService = conversationService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    public void deleteInactiveConversations() {
        try {
            Instant cutoff = clock.instant().minus(properties.getRetention());
            int deletedCount = conversationService.deleteInactiveConversations(cutoff);
            log.info("AI calendar conversation cleanup finished. deletedCount={}", deletedCount);
        } catch (RuntimeException exception) {
            log.error("AI calendar conversation cleanup failed. errorCode=AI_CALENDAR_CLEANUP_FAILED");
        }
    }
}
