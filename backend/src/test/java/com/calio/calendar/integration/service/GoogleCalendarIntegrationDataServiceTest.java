package com.calio.calendar.integration.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
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
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final RecurrenceEventRepository recurrenceEventRepository =
            mock(RecurrenceEventRepository.class);
    private final RecurrenceEventOverrideRepository overrideRepository =
            mock(RecurrenceEventOverrideRepository.class);
    private final GoogleOperationJobPersistenceService operationJobPersistenceService =
            mock(GoogleOperationJobPersistenceService.class);
    private final GoogleOperationLeaseService operationLeaseService =
            mock(GoogleOperationLeaseService.class);
    private final GoogleCalendarEventMapping eventMapping = mock(GoogleCalendarEventMapping.class);

    @Test
    @DisplayName("FULL SYNC시 각 mapping batch에서 operation lease를 갱신한다")
    void givenUnseenMappings_whenFinalizeFullSync_thenDeletesByBatchAndRenewsLease() {
        // given
        Event event = mock(Event.class);
        when(eventMapping.getId()).thenReturn(10L);
        when(eventMapping.getExternalEventId()).thenReturn("unseen-event");
        when(eventMapping.getEvent()).thenReturn(event);
        when(event.getId()).thenReturn(20L);
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
                eventRepository,
                recurrenceEventRepository,
                overrideRepository,
                null,
                operationLeaseService,
                operationJobPersistenceService
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
        verify(eventRepository).deleteAllByIds(List.of(20L));
        verify(eventMappingQueryService, times(2))
                .listEventMappingBatch(eq(1L), any(Long.class), eq(500));
        verify(integrationCommandService).saveNextSyncToken(1L, "next-token");
        verify(operationLeaseService, times(6)).extend(9L, 2L, "run-1");
        verify(operationJobPersistenceService).succeed(9L, 2L, "run-1");
    }
}
