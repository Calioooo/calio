package com.calio.calendar.integration.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.integration.connection.service.GoogleCalendarIntegrationCommandService;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationLeaseService;
import com.calio.calendar.recurrence.service.RecurrenceEventCommandService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarIntegrationDataServiceTest {

    private final GoogleCalendarIntegrationCommandService integrationCommandService =
            mock(GoogleCalendarIntegrationCommandService.class);
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService =
            mock(GoogleCalendarEventMappingQueryService.class);
    private final GoogleCalendarEventMappingCommandService eventMappingCommandService =
            mock(GoogleCalendarEventMappingCommandService.class);
    private final GoogleCalendarRecurrenceMappingQueryService recurrenceMappingQueryService =
            mock(GoogleCalendarRecurrenceMappingQueryService.class);
    private final GoogleCalendarRecurrenceMappingCommandService recurrenceMappingCommandService =
            mock(GoogleCalendarRecurrenceMappingCommandService.class);
    private final EventCommandService eventCommandService = mock(EventCommandService.class);
    private final RecurrenceEventCommandService recurrenceEventCommandService =
            mock(RecurrenceEventCommandService.class);
    private final GoogleOperationJobService operationJobPersistenceService =
            mock(GoogleOperationJobService.class);
    private final GoogleOperationLeaseService operationLeaseService =
            mock(GoogleOperationLeaseService.class);
    private final GoogleOperationJobQueryService operationJobQueryService =
            mock(GoogleOperationJobQueryService.class);
    private final GoogleCalendarEventMapping eventMapping = mock(GoogleCalendarEventMapping.class);

    @Test
    @DisplayName("FULL SYNC시 각 mapping batch에서 operation lease를 갱신한다")
    void givenUnseenMappings_whenFinalizeFullSync_thenDeletesByBatchAndRenewsLease() {
        // given
        Event event = mock(Event.class);
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        when(eventMapping.getId()).thenReturn(10L);
        when(eventMapping.getExternalEventId()).thenReturn("unseen-event");
        when(eventMapping.getEvent()).thenReturn(event);
        when(eventMapping.getIntegration()).thenReturn(integration);
        when(integration.getId()).thenReturn(1L);
        when(integration.getAccountId()).thenReturn(2L);
        when(event.getId()).thenReturn(20L);
        when(operationJobQueryService.hasPendingOutboundJob(any(), any(), any())).thenReturn(false);
        when(recurrenceMappingQueryService.listOverrideMappingBatch(1L, 0L, 500))
                .thenReturn(List.of());
        when(eventMappingQueryService.listEventMappingBatch(1L, 0L, 500))
                .thenReturn(List.of(eventMapping));
        when(eventMappingQueryService.listEventMappingBatch(1L, 10L, 500))
                .thenReturn(List.of());
        when(recurrenceMappingQueryService.listRecurrenceEventMappingBatch(1L, 0L, 500))
                .thenReturn(List.of());

        GoogleCalendarIntegrationDataService service = new GoogleCalendarIntegrationDataService(
                integrationCommandService,
                eventMappingQueryService,
                eventMappingCommandService,
                recurrenceMappingQueryService,
                recurrenceMappingCommandService,
                eventCommandService,
                recurrenceEventCommandService,
                null,
                operationLeaseService,
                operationJobPersistenceService,
                operationJobQueryService
        );

        // when
        service.completeSyncRun(
                9L,
                2L,
                1L,
                "run-1",
                GoogleCalendarSyncMode.FULL,
                Set.of(),
                Set.of(),
                Set.of(),
                "next-token"
        );

        // then
        verify(eventMappingCommandService).deleteEventMappingsWithIds(List.of(10L));
        verify(eventCommandService).deleteEventsByIds(List.of(20L));
        verify(eventMappingQueryService, times(2))
                .listEventMappingBatch(eq(1L), any(Long.class), eq(500));
        verify(integrationCommandService).changeNextSyncToken(1L, "next-token");
        verify(operationLeaseService, times(6)).extend(9L, 2L, "run-1");
        verify(operationJobPersistenceService).completeSyncRun(9L, 2L, "run-1");
    }
}
