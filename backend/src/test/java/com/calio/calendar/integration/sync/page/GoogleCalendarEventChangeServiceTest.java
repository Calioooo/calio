package com.calio.calendar.integration.sync.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache;
import java.time.Instant;
import java.util.HashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarEventChangeServiceTest {

    private final GoogleCalendarEventMappingCommandService eventMappingCommandService =
            mock(GoogleCalendarEventMappingCommandService.class);
    private final EventCommandService eventCommandService = mock(EventCommandService.class);
    private final GoogleOperationJobQueryService operationJobQueryService =
            mock(GoogleOperationJobQueryService.class);
    private final GoogleOperationJobService operationJobService = mock(GoogleOperationJobService.class);
    private final GoogleCalendarEventChangeService service = new GoogleCalendarEventChangeService(
            eventMappingCommandService,
            eventCommandService,
            operationJobQueryService,
            operationJobService
    );

    @Test
    @DisplayName("다른 provider eTag와 진행 중 outbound Job이 있으면 Event 내용을 덮어쓰지 않고 충돌 처리한다")
    void givenChangedProviderEtagAndPendingOutboundJob_whenApplyUpsert_thenMarksMappingConflicted() {
        // given
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        GoogleCalendarConnection connection = mock(GoogleCalendarConnection.class);
        Event event = mock(Event.class);
        when(integration.getAccountId()).thenReturn(10L);
        when(connection.getId()).thenReturn(20L);
        when(connection.getAccountId()).thenReturn(10L);
        when(event.getId()).thenReturn(30L);
        GoogleCalendarEventMapping mapping = new GoogleCalendarEventMapping(
                connection,
                event,
                "provider-event-id",
                "etag-before"
        );
        GoogleCalendarPageRecordCache cache = new GoogleCalendarPageRecordCache(
                new HashMap<>(java.util.Map.of("provider-event-id", mapping)),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        );
        EventUpsert upsert = new EventUpsert(
                "provider-event-id",
                "etag-after",
                "Changed by Google",
                null,
                new NormalizedEventSchedule(
                        Instant.parse("2026-08-15T00:00:00Z"),
                        Instant.parse("2026-08-15T01:00:00Z"),
                        false,
                        "UTC"
                )
        );
        GoogleCalendarPageOwnership ownership = new GoogleCalendarPageOwnership(40L, "worker-token");
        GoogleCalendarEffectiveScope scope = GoogleCalendarEffectiveScope.event(30L);
        when(operationJobQueryService.hasPendingOutboundJob(10L, 20L, scope)).thenReturn(true);

        // when
        service.applyUpsert(connection, upsert, cache, null, null, ownership);

        // then
        assertThat(mapping.isConflicted()).isTrue();
        assertThat(mapping.getProviderEtag()).isEqualTo("etag-before");
        verify(event, never()).replace(
                eq("Changed by Google"), eq(null), eq(upsert.schedule().startAt()),
                eq(upsert.schedule().endAt()), eq(false), eq("UTC")
        );
        verify(operationJobService).recordSyncConflict(40L, 10L, "worker-token");
    }
}
