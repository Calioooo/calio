package com.calio.calendar.integration.mapping.domain;

import static com.calio.calendar.integration.mapping.domain.GoogleCalendarProviderChangeAction.APPLY;
import static com.calio.calendar.integration.mapping.domain.GoogleCalendarProviderChangeAction.IGNORE;
import static com.calio.calendar.integration.mapping.domain.GoogleCalendarProviderChangeAction.MARK_CONFLICT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarRecurrenceMappingTest {

    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");

    @Test
    @DisplayName("반복 일정 mapping은 local 변경 또는 canonical detach 상태의 다른 Google ETag를 충돌로 판정한다")
    void givenLocalOrDetachedMapping_whenEvaluateGoogleUpsert_thenMarksConflict() {
        GoogleCalendarRecurrenceEventMapping locallyModified = recurrenceMapping();
        locallyModified.markLocalModification(NOW);
        GoogleCalendarRecurrenceEventMapping detached = recurrenceMapping();
        detached.detachCanonicalRecurrenceEvent(NOW);

        assertThat(locallyModified.evaluateGoogleUpsert("changed-etag")).isEqualTo(MARK_CONFLICT);
        assertThat(detached.evaluateGoogleUpsert("changed-etag")).isEqualTo(MARK_CONFLICT);
        assertThat(recurrenceMapping().evaluateGoogleUpsert("changed-etag")).isEqualTo(APPLY);
    }

    @Test
    @DisplayName("반복 일정의 local 변경은 override ETag와 비교해 series 충돌로 전이하지 않는다")
    void givenLocallyModifiedRecurrenceEvent_whenEvaluateOverrideUpsert_thenIgnoresOverrideEtag() {
        GoogleCalendarRecurrenceEventMapping recurrenceMapping = recurrenceMapping();
        recurrenceMapping.markLocalModification(NOW);
        GoogleCalendarRecurrenceOverrideMapping overrideMapping = new GoogleCalendarRecurrenceOverrideMapping(
                recurrenceMapping,
                mock(RecurrenceEventOverride.class),
                "override-id",
                "override-etag"
        );

        assertThat(overrideMapping.evaluateGoogleUpsert("different-override-etag"))
                .isEqualTo(IGNORE);
    }

    private GoogleCalendarRecurrenceEventMapping recurrenceMapping() {
        return new GoogleCalendarRecurrenceEventMapping(
                integration(),
                mock(RecurrenceEvent.class),
                "recurrence-event-id",
                "recurrence-etag"
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
