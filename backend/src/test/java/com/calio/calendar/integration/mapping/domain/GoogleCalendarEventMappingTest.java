package com.calio.calendar.integration.mapping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarEventMappingTest {

    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");

    @Test
    @DisplayName("연결된 ACTIVE mapping만 canonical Event의 로컬 수정을 막는다")
    void givenMappingLifecycleStates_whenCheckLocalMutation_thenBlocksOnlyConnectedActiveMapping() {
        // given
        GoogleCalendarIntegration connectedIntegration = integration();
        GoogleCalendarEventMapping activeMapping = mapping(connectedIntegration);
        GoogleCalendarEventMapping conflictedMapping = mapping(integration());
        conflictedMapping.markConflicted();
        GoogleCalendarIntegration disconnectedIntegration = integration();
        disconnectedIntegration.disconnect(NOW);
        GoogleCalendarEventMapping disconnectedMapping = mapping(disconnectedIntegration);

        // then
        assertThat(activeMapping.blocksLocalMutation()).isTrue();
        assertThat(conflictedMapping.blocksLocalMutation()).isFalse();
        assertThat(disconnectedMapping.blocksLocalMutation()).isFalse();
    }

    private GoogleCalendarEventMapping mapping(GoogleCalendarIntegration integration) {
        return new GoogleCalendarEventMapping(
                integration,
                mock(Event.class),
                "external-event-id",
                "provider-etag"
        );
    }

    private GoogleCalendarIntegration integration() {
        return new GoogleCalendarIntegration(
                1L,
                "google-subject",
                "user@example.com",
                "encrypted-refresh-token",
                "encrypted-access-token",
                NOW.plusSeconds(3600),
                NOW
        );
    }
}
