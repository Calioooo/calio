package com.calio.calendar.integration.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionCommandService;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationLeaseService;
import com.calio.calendar.recurrence.service.RecurrenceEventCommandService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarIntegrationDataServiceTest {

    private final GoogleCalendarConnectionCommandService connectionCommandService =
            mock(GoogleCalendarConnectionCommandService.class);
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
    private final GoogleCalendarRecurrenceEventMapping recurrenceMapping =
            mock(GoogleCalendarRecurrenceEventMapping.class);
    private final GoogleCalendarRecurrenceOverrideMapping recurrenceOverrideMapping =
            mock(GoogleCalendarRecurrenceOverrideMapping.class);

    @Test
    @DisplayName("FULL SYNC시 각 mapping batch에서 operation lease를 갱신한다")
    void givenUnseenMappings_whenFinalizeFullSync_thenDeletesByBatchAndRenewsLease() {
        // given
        GoogleCalendarConnection connection = mock(GoogleCalendarConnection.class);
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        when(eventMapping.getId()).thenReturn(10L);
        when(eventMapping.getExternalEventId()).thenReturn("unseen-event");
        when(eventMapping.getEventId()).thenReturn(20L);
        when(eventMapping.getConnection()).thenReturn(connection);
        when(connection.getId()).thenReturn(1L);
        when(connection.getAccountId()).thenReturn(2L);
        when(connection.getIntegration()).thenReturn(integration);
        when(integration.getId()).thenReturn(3L);
        when(connectionCommandService.lockConnectedConnectionById(1L)).thenReturn(connection);
        when(operationJobQueryService.hasPendingOutboundJob(any(), any(), any())).thenReturn(false);
        when(recurrenceMappingQueryService.listOverrideMappingBatch(1L, 0L, 500))
                .thenReturn(List.of());
        when(eventMappingQueryService.listEventMappingBatch(1L, 0L, 500))
                .thenReturn(List.of(eventMapping));
        when(eventMappingQueryService.listEventMappingBatch(1L, 10L, 500))
                .thenReturn(List.of());
        when(eventMappingQueryService.listEventIdsWithMappings(List.of(20L))).thenReturn(List.of());
        when(recurrenceMappingQueryService.listRecurrenceEventMappingBatch(1L, 0L, 500))
                .thenReturn(List.of());

        GoogleCalendarIntegrationDataService service = new GoogleCalendarIntegrationDataService(
                connectionCommandService,
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
        verify(connectionCommandService).changeNextSyncToken(connection, "next-token");
        verify(operationLeaseService, times(6)).extend(9L, 2L, "run-1");
        verify(operationJobPersistenceService).completeSyncRun(9L, 2L, "run-1");
    }

    @Test
    @DisplayName("다른 connection mapping이 남아 있으면 FULL SYNC cleanup은 Event를 삭제하지 않는다")
    void givenUnseenMappingWithAnotherConnectionMapping_whenFinalizeFullSync_thenKeepsEvent() {
        // given
        GoogleCalendarConnection connection = mock(GoogleCalendarConnection.class);
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        when(eventMapping.getId()).thenReturn(10L);
        when(eventMapping.getExternalEventId()).thenReturn("unseen-event");
        when(eventMapping.getEventId()).thenReturn(20L);
        when(eventMapping.getConnection()).thenReturn(connection);
        when(connection.getId()).thenReturn(1L);
        when(connection.getAccountId()).thenReturn(2L);
        when(connection.getIntegration()).thenReturn(integration);
        when(integration.getId()).thenReturn(3L);
        when(connectionCommandService.lockConnectedConnectionById(1L)).thenReturn(connection);
        when(operationJobQueryService.hasPendingOutboundJob(any(), any(), any())).thenReturn(false);
        when(recurrenceMappingQueryService.listOverrideMappingBatch(1L, 0L, 500))
                .thenReturn(List.of());
        when(eventMappingQueryService.listEventMappingBatch(1L, 0L, 500))
                .thenReturn(List.of(eventMapping));
        when(eventMappingQueryService.listEventMappingBatch(1L, 10L, 500))
                .thenReturn(List.of());
        when(eventMappingQueryService.listEventIdsWithMappings(List.of(20L)))
                .thenReturn(List.of(20L));
        when(recurrenceMappingQueryService.listRecurrenceEventMappingBatch(1L, 0L, 500))
                .thenReturn(List.of());
        GoogleCalendarIntegrationDataService service = new GoogleCalendarIntegrationDataService(
                connectionCommandService,
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
                9L, 2L, 1L, "run-1", GoogleCalendarSyncMode.FULL,
                Set.of(), Set.of(), Set.of(), "next-token"
        );

        // then
        verify(eventMappingCommandService).deleteEventMappingsWithIds(List.of(10L));
        verify(eventCommandService, never()).deleteEventsByIds(any());
    }

    @Test
    @DisplayName("같은 recurrence event를 참조하는 mapping이 남아 있으면 FULL SYNC cleanup은 recurrence aggregate를 삭제하지 않는다")
    void givenUnseenRecurrenceMappingWithRemainingRecurrenceReference_whenFinalizeFullSync_thenKeepsAggregate() {
        GoogleCalendarConnection connection = mock(GoogleCalendarConnection.class);
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        when(recurrenceMapping.getId()).thenReturn(10L);
        when(recurrenceMapping.getExternalEventId()).thenReturn("unseen-recurrence");
        when(recurrenceMapping.getRecurrenceEventId()).thenReturn(40L);
        when(recurrenceMapping.getConnection()).thenReturn(connection);
        when(connection.getAccountId()).thenReturn(2L);
        when(connection.getIntegration()).thenReturn(integration);
        when(integration.getId()).thenReturn(3L);
        when(connectionCommandService.lockConnectedConnectionById(1L)).thenReturn(connection);
        when(operationJobQueryService.hasPendingOutboundJob(any(), any(), any())).thenReturn(false);
        when(recurrenceMappingQueryService.listOverrideMappingBatch(1L, 0L, 500))
                .thenReturn(List.of());
        when(eventMappingQueryService.listEventMappingBatch(1L, 0L, 500)).thenReturn(List.of());
        when(recurrenceMappingQueryService.listRecurrenceEventMappingBatch(1L, 0L, 500))
                .thenReturn(List.of(recurrenceMapping));
        when(recurrenceMappingQueryService.listRecurrenceEventMappingBatch(1L, 10L, 500))
                .thenReturn(List.of());
        when(recurrenceMappingQueryService.listRecurrenceEventIdsWithMappings(List.of(40L)))
                .thenReturn(List.of(40L));

        GoogleCalendarIntegrationDataService service = new GoogleCalendarIntegrationDataService(
                connectionCommandService, eventMappingQueryService, eventMappingCommandService,
                recurrenceMappingQueryService, recurrenceMappingCommandService, eventCommandService,
                recurrenceEventCommandService, null, operationLeaseService,
                operationJobPersistenceService, operationJobQueryService);

        service.completeSyncRun(9L, 2L, 1L, "run-1", GoogleCalendarSyncMode.FULL,
                Set.of(), Set.of(), Set.of(), "next-token");

        verify(recurrenceMappingCommandService).deleteRecurrenceEventMappingsWithIds(List.of(10L));
        verify(recurrenceEventCommandService, never()).deleteRecurrenceEventsByIds(any());
        verify(recurrenceEventCommandService, never()).deleteRecurrenceOverridesByRecurrenceEventIds(any());
        verify(eventCommandService, never()).deleteEventsByRecurrenceEventIds(any());
    }

    @Test
    @DisplayName("다른 connection override mapping이 남아 있으면 FULL SYNC cleanup은 canonical override를 삭제하지 않는다")
    void givenUnseenOverrideWithAnotherConnectionMapping_whenFinalizeFullSync_thenKeepsCanonicalOverride() {
        GoogleCalendarConnection connection = mock(GoogleCalendarConnection.class);
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        Instant origin = Instant.parse("2026-09-01T00:00:00Z");
        when(recurrenceMapping.getRecurrenceEventId()).thenReturn(40L);
        when(recurrenceMapping.getConnection()).thenReturn(connection);
        when(recurrenceOverrideMapping.getId()).thenReturn(10L);
        when(recurrenceOverrideMapping.getExternalEventId()).thenReturn("unseen-override");
        when(recurrenceOverrideMapping.getOriginStartAt()).thenReturn(origin);
        when(recurrenceOverrideMapping.getRecurrenceEventMapping()).thenReturn(recurrenceMapping);
        when(connection.getAccountId()).thenReturn(2L);
        when(connection.getIntegration()).thenReturn(integration);
        when(integration.getId()).thenReturn(3L);
        when(connectionCommandService.lockConnectedConnectionById(1L)).thenReturn(connection);
        when(operationJobQueryService.hasPendingOutboundJob(any(), any(), any())).thenReturn(false);
        when(recurrenceMappingQueryService.listOverrideMappingBatch(1L, 0L, 500))
                .thenReturn(List.of(recurrenceOverrideMapping));
        when(recurrenceMappingQueryService.listOverrideMappingBatch(1L, 10L, 500))
                .thenReturn(List.of());
        GoogleCalendarRecurrenceOverrideMapping remainingOverrideMapping =
                mock(GoogleCalendarRecurrenceOverrideMapping.class);
        when(remainingOverrideMapping.getRecurrenceEventMapping()).thenReturn(recurrenceMapping);
        when(remainingOverrideMapping.getOriginStartAt()).thenReturn(origin);
        when(recurrenceMappingQueryService.listOverrideMappingsByRecurrenceEventIds(List.of(40L)))
                .thenReturn(List.of(remainingOverrideMapping));
        when(eventMappingQueryService.listEventMappingBatch(1L, 0L, 500)).thenReturn(List.of());
        when(recurrenceMappingQueryService.listRecurrenceEventMappingBatch(1L, 0L, 500))
                .thenReturn(List.of());

        GoogleCalendarIntegrationDataService service = new GoogleCalendarIntegrationDataService(
                connectionCommandService, eventMappingQueryService, eventMappingCommandService,
                recurrenceMappingQueryService, recurrenceMappingCommandService, eventCommandService,
                recurrenceEventCommandService, null, operationLeaseService,
                operationJobPersistenceService, operationJobQueryService);

        service.completeSyncRun(9L, 2L, 1L, "run-1", GoogleCalendarSyncMode.FULL,
                Set.of(), Set.of(), Set.of(), "next-token");

        verify(recurrenceMappingCommandService).deleteOverrideMappingsWithIds(List.of(10L));
        verify(recurrenceEventCommandService, never())
                .deleteRecurrenceOverridesByRecurrenceEventIdAndOriginStartAts(any(), any());
    }
}
