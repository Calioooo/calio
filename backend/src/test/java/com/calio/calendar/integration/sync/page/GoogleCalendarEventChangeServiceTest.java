package com.calio.calendar.integration.sync.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.event.service.EventQueryService;
import com.calio.calendar.external.google.service.dto.NormalizedEventSchedule;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarNormalizedPage.EventUpsert;
import com.calio.calendar.integration.sync.page.dto.GoogleCalendarPageRecordCache;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleCalendarEventChangeServiceTest {

    private final GoogleCalendarEventMappingCommandService eventMappingCommandService =
            mock(GoogleCalendarEventMappingCommandService.class);
    private final GoogleCalendarEventMappingQueryService eventMappingQueryService =
            mock(GoogleCalendarEventMappingQueryService.class);
    private final EventCommandService eventCommandService = mock(EventCommandService.class);
    private final EventQueryService eventQueryService = mock(EventQueryService.class);
    private final GoogleOperationJobQueryService operationJobQueryService =
            mock(GoogleOperationJobQueryService.class);
    private final GoogleOperationJobService operationJobService = mock(GoogleOperationJobService.class);
    private final GoogleCalendarEventChangeService service = new GoogleCalendarEventChangeService(
            eventMappingCommandService, eventMappingQueryService,
            eventCommandService,
            eventQueryService,
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
        when(integration.getId()).thenReturn(21L);
        when(connection.getId()).thenReturn(20L);
        when(connection.getAccountId()).thenReturn(10L);
        when(connection.getIntegration()).thenReturn(integration);
        when(event.getId()).thenReturn(30L);
        GoogleCalendarEventMapping mapping = new GoogleCalendarEventMapping(
                connection,
                30L,
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
        when(operationJobQueryService.hasPendingOutboundJob(10L, 21L, scope)).thenReturn(true);

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

    @Test
    @DisplayName("삭제된 Event의 Google 변경은 local Event를 재생성하거나 mapping을 갱신하지 않는다")
    void givenMissingEventWithoutPendingJob_whenApplyUpsert_thenIgnoresProviderChange() {
        // given
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        GoogleCalendarConnection connection = mock(GoogleCalendarConnection.class);
        when(integration.getAccountId()).thenReturn(10L);
        when(integration.getId()).thenReturn(21L);
        when(connection.getAccountId()).thenReturn(10L);
        when(connection.getIntegration()).thenReturn(integration);
        GoogleCalendarEventMapping mapping = new GoogleCalendarEventMapping(
                connection, 30L, "provider-event-id", "etag-before");
        GoogleCalendarPageRecordCache cache = new GoogleCalendarPageRecordCache(
                new HashMap<>(java.util.Map.of("provider-event-id", mapping)),
                new HashMap<>(), new HashMap<>(), new HashMap<>());
        EventUpsert upsert = new EventUpsert(
                "provider-event-id", "etag-after", "Changed by Google", null,
                new NormalizedEventSchedule(
                        Instant.parse("2026-08-15T00:00:00Z"),
                        Instant.parse("2026-08-15T01:00:00Z"), false, "UTC"));
        when(operationJobQueryService.hasPendingOutboundJob(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(eventQueryService.getEventIfExists(10L, 30L)).thenReturn(Optional.empty());

        // when
        service.applyUpsert(connection, upsert, cache, null, null,
                new GoogleCalendarPageOwnership(40L, "worker-token"));

        // then
        assertThat(mapping.getProviderEtag()).isEqualTo("etag-before");
        verify(eventCommandService, never()).createEvent(org.mockito.ArgumentMatchers.any());
        verify(eventCommandService, never()).updateEvent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Google cancellation 후 다른 connection mapping이 남아 있으면 Event를 유지한다")
    void givenAnotherConnectionMapping_whenApplyCancellation_thenKeepsEvent() {
        // given
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        GoogleCalendarConnection connection = mock(GoogleCalendarConnection.class);
        when(integration.getId()).thenReturn(21L);
        when(connection.getAccountId()).thenReturn(10L);
        when(connection.getIntegration()).thenReturn(integration);
        GoogleCalendarEventMapping mapping = new GoogleCalendarEventMapping(
                connection, 30L, "provider-event-id", "etag");
        GoogleCalendarPageRecordCache cache = new GoogleCalendarPageRecordCache(
                new HashMap<>(java.util.Map.of("provider-event-id", mapping)),
                new HashMap<>(), new HashMap<>(), new HashMap<>());
        when(operationJobQueryService.hasPendingOutboundJob(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(eventMappingQueryService.listEventIdsWithMappings(List.of(30L)))
                .thenReturn(List.of(30L));

        // when
        service.applyCancellation(
                "provider-event-id", cache, new GoogleCalendarPageOwnership(40L, "worker-token"));

        // then
        verify(eventMappingCommandService).deleteEventMapping(mapping);
        verify(eventCommandService, never()).deleteEvent(org.mockito.ArgumentMatchers.any());
    }
}
