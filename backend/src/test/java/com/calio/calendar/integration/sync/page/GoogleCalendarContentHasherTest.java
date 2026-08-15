package com.calio.calendar.integration.sync.page;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.RecurrenceEventUpsert;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarContentHasherTest {

    @Test
    @DisplayName("동일한 provider-owned Event 내용은 항상 같은 hash를 만든다")
    void givenEquivalentEventContent_whenHashing_thenReturnsSameHash() {
        EventUpsert first = eventUpsert();
        EventUpsert second = eventUpsert();

        assertThat(GoogleCalendarContentHasher.hash(first))
                .isEqualTo(GoogleCalendarContentHasher.hash(second));
    }

    @Test
    @DisplayName("Event와 recurrence-event는 같은 필드 값이어도 서로 다른 hash를 만든다")
    void givenSameFieldsForDifferentContentTypes_whenHashing_thenSeparatesHashes() {
        NormalizedEventSchedule schedule = schedule();
        EventUpsert event = new EventUpsert("event-1", "Title", null, schedule);
        RecurrenceEventUpsert recurrenceEvent = new RecurrenceEventUpsert(
                "recurrence-event-1",
                "Title",
                null,
                schedule,
                List.of()
        );

        assertThat(GoogleCalendarContentHasher.hash(event))
                .isNotEqualTo(GoogleCalendarContentHasher.hash(recurrenceEvent));
    }

    private EventUpsert eventUpsert() {
        return new EventUpsert("event-1", "Title", null, schedule());
    }

    private NormalizedEventSchedule schedule() {
        return new NormalizedEventSchedule(
                Instant.parse("2026-08-01T09:00:00Z"),
                Instant.parse("2026-08-01T10:00:00Z"),
                false,
                "UTC"
        );
    }
}
