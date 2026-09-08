package com.calio.calendar.integration.sync.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.operation.repository.GoogleOperationJobRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleOperationJobQueryServiceTest {
    private final GoogleOperationJobRepository repository = mock();
    private final GoogleOperationJobQueryService service = new GoogleOperationJobQueryService(repository);

    @Test
    @DisplayName("master inbound cleanup은 같은 recurrence aggregate의 pending outbound Job을 보호한다")
    void recurrenceMasterScopeQueriesAggregateJobs() {
        when(repository.existsPendingRecurrenceAggregateJob(10L, 20L, 40L)).thenReturn(true);

        assertThat(service.hasPendingOutboundJob(
                10L, 20L, GoogleCalendarEffectiveScope.recurrenceEvent(40L))).isTrue();

        verify(repository).existsPendingRecurrenceAggregateJob(10L, 20L, 40L);
    }

    @Test
    @DisplayName("override inbound cleanup은 같은 recurrence의 exact origin Job만 보호한다")
    void recurrenceOverrideScopeQueriesExactOriginJob() {
        Instant origin = Instant.parse("2026-09-04T00:00:00Z");

        service.hasPendingOutboundJob(
                10L, 20L, GoogleCalendarEffectiveScope.recurrenceOverride(40L, origin));

        verify(repository).existsPendingRecurrenceOverrideJob(10L, 20L, 40L, origin);
    }
}
