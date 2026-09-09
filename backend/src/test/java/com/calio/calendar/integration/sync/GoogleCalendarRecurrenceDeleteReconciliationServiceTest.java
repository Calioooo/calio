package com.calio.calendar.integration.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTimeResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.service.GoogleCalendarAccessTokenService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarRecurrenceDeleteReconciliationServiceTest {

    @Test
    @DisplayName("재연결 전 pending master delete는 최신 provider etag로 삭제한 뒤 retained mapping을 제거한다")
    void pendingDeleteRemovesProviderAggregateBeforeInboundSync() {
        GoogleCalendarRecurrenceMappingQueryService mappings = mock();
        GoogleCalendarRecurrenceMappingCommandService mappingCommands = mock();
        GoogleCalendarAccessTokenService tokens = mock();
        GoogleCalendarEventsClient client = mock();
        GoogleCalendarConnection connection = mock();
        GoogleCalendarRecurrenceEventMapping mapping = mock();
        when(connection.getId()).thenReturn(30L);
        when(mapping.isProviderDeletePending()).thenReturn(true);
        when(mapping.getExternalEventId()).thenReturn("master-1");
        when(mappings.listRecurrenceEventMappings(30L)).thenReturn(List.of(mapping));
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(client.getEvent("token", "master-1")).thenReturn(Optional.of(provider("master-1", "new-etag")));

        GoogleCalendarRecurrenceDeleteReconciliationService service =
                new GoogleCalendarRecurrenceDeleteReconciliationService(
                        mappings, mappingCommands, tokens, client);

        service.reconcilePendingDeletes(connection);

        verify(client).deleteEvent("token", "master-1", "new-etag");
        verify(mappingCommands).deleteRecurrenceAggregateMappings(mapping);
    }

    @Test
    @DisplayName("원격 master가 이미 없으면 provider delete 없이 retained mapping을 제거한다")
    void givenMissingProviderAggregate_whenReconcilingPendingDelete_thenRemovesRetainedMapping() {
        // given
        GoogleCalendarRecurrenceMappingQueryService mappings = mock();
        GoogleCalendarRecurrenceMappingCommandService mappingCommands = mock();
        GoogleCalendarAccessTokenService tokens = mock();
        GoogleCalendarEventsClient client = mock();
        GoogleCalendarConnection connection = mock();
        GoogleCalendarRecurrenceEventMapping mapping = mock();
        when(connection.getId()).thenReturn(30L);
        when(mapping.isProviderDeletePending()).thenReturn(true);
        when(mapping.getExternalEventId()).thenReturn("master-1");
        when(mappings.listRecurrenceEventMappings(30L)).thenReturn(List.of(mapping));
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(client.getEvent("token", "master-1")).thenReturn(Optional.empty());

        GoogleCalendarRecurrenceDeleteReconciliationService service =
                new GoogleCalendarRecurrenceDeleteReconciliationService(
                        mappings, mappingCommands, tokens, client);

        // when
        service.reconcilePendingDeletes(connection);

        // then
        verify(client, never()).deleteEvent(any(), any(), any());
        verify(mappingCommands).deleteRecurrenceAggregateMappings(mapping);
    }

    private GoogleCalendarEventResponse provider(String id, String etag) {
        return new GoogleCalendarEventResponse(id, "confirmed", etag,
                Instant.parse("2026-09-01T00:00:00Z"), "Daily", null, List.of(), null,
                null,
                new GoogleCalendarEventTimeResponse(null, "2026-09-01T09:00:00Z", "UTC"),
                new GoogleCalendarEventTimeResponse(null, "2026-09-01T10:00:00Z", "UTC"));
    }
}
