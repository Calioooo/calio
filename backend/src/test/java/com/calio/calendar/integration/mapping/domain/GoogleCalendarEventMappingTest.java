package com.calio.calendar.integration.mapping.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static com.calio.calendar.integration.mapping.domain.GoogleCalendarProviderChangeAction.APPLY;
import static com.calio.calendar.integration.mapping.domain.GoogleCalendarProviderChangeAction.IGNORE;
import static com.calio.calendar.integration.mapping.domain.GoogleCalendarProviderChangeAction.MARK_CONFLICT;
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

    @Test
    @DisplayName("Google 변경은 mapping 상태와 마지막 provider ETag에 따라 적용·무시·충돌로 판정한다")
    void givenMappingStates_whenEvaluateGoogleUpsert_thenReturnsProviderChangeAction() {
        GoogleCalendarEventMapping activeMapping = mapping(integration());
        GoogleCalendarEventMapping locallyModifiedMapping = mapping(integration());
        locallyModifiedMapping.markLocalModification(NOW);
        GoogleCalendarEventMapping detachedMapping = mapping(integration());
        detachedMapping.detachCanonicalEvent(NOW);
        GoogleCalendarEventMapping conflictedMapping = mapping(integration());
        conflictedMapping.markConflicted();

        assertThat(activeMapping.evaluateGoogleUpsert("changed-etag")).isEqualTo(APPLY);
        assertThat(activeMapping.evaluateGoogleUpsert("provider-etag")).isEqualTo(IGNORE);
        assertThat(locallyModifiedMapping.evaluateGoogleUpsert("changed-etag")).isEqualTo(MARK_CONFLICT);
        assertThat(detachedMapping.evaluateGoogleUpsert("changed-etag")).isEqualTo(MARK_CONFLICT);
        assertThat(conflictedMapping.evaluateGoogleUpsert("changed-etag")).isEqualTo(IGNORE);
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
