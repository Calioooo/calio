package com.calio.calendar.aicalendar.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.aicalendar.config.CalendarConversationProperties;
import com.calio.calendar.aicalendar.service.CalendarConversationPersistenceService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarConversationCleanupSchedulerTest {

    @Mock
    private CalendarConversationPersistenceService persistenceService;

    @Test
    @DisplayName("cleanup scheduler는 실행 시각에서 retention을 뺀 cutoff로 대화 정리를 위임한다")
    void givenFixedClock_whenDeleteInactiveConversations_thenDelegatesCleanupWithRetentionCutoff() {
        // given
        Instant now = Instant.parse("2026-07-31T04:00:00Z");
        Instant cutoff = Instant.parse("2026-07-01T04:00:00Z");
        CalendarConversationProperties properties = new CalendarConversationProperties();
        properties.setRetention(Duration.ofDays(30));
        CalendarConversationCleanupScheduler scheduler = new CalendarConversationCleanupScheduler(
                persistenceService,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        when(persistenceService.deleteInactiveConversations(cutoff)).thenReturn(2);

        // when
        scheduler.deleteInactiveConversations();

        // then
        verify(persistenceService).deleteInactiveConversations(cutoff);
    }
}
