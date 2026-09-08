package com.calio.calendar.integration.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTimeResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.service.GoogleCalendarAccessTokenService;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionQueryService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarRecurrenceMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJobKind;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceMasterJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class GoogleCalendarRecurrenceJobServiceTest {
    private final GoogleCalendarConnectionQueryService connections = mock();
    private final GoogleCalendarRecurrenceMappingQueryService mappings = mock();
    private final GoogleCalendarRecurrenceMappingCommandService mappingCommands = mock();
    private final GoogleCalendarAccessTokenService tokens = mock();
    private final GoogleCalendarEventsClient client = mock();
    private final ObjectMapper objectMapper = mock();
    private final GoogleOperationJobService jobs = mock();
    private final TransactionTemplate transaction = mock();
    private GoogleCalendarRecurrenceJobService service;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> invocation.getArgument(0, TransactionCallback.class)
                .doInTransaction(null)).when(transaction).execute(any());
        lenient().doAnswer(invocation -> {
            invocation.getArgument(0, Consumer.class).accept(null);
            return null;
        }).when(transaction).executeWithoutResult(any());
        service = new GoogleCalendarRecurrenceJobService(connections, mappings, mappingCommands,
                tokens, client, objectMapper, jobs, transaction);
    }

    @Test
    @DisplayName("DISCONNECTED master mapping은 provider I/O 없이 실제 mutation에 대해서만 localChanged가 된다")
    void disconnectedMasterUpdateMarksLocalChange() {
        GoogleCalendarConnection connection = connection(30L);
        connection.disconnect(Instant.parse("2026-09-01T00:00:00Z"));
        GoogleCalendarRecurrenceEventMapping mapping = master(connection);
        when(mappings.listRecurrenceEventMappingsForJob(20L, 40L)).thenReturn(List.of(mapping));
        when(objectMapper.readValue("payload", GoogleRecurrenceMasterJobPayload.class)).thenReturn(masterPayload());

        service.execute(job(GoogleCalendarRecurrenceJobKind.MASTER_UPDATE, null), "worker");

        assertThat(mapping.isLocalChanged()).isTrue();
        verifyNoInteractions(tokens, client);
        verify(jobs).succeed(50L, 10L, "worker");
    }

    @Test
    @DisplayName("여러 retained Connection 중 CONNECTED만 provider write하고 SYNC_ERROR mapping은 localChanged로 남긴다")
    void routesAcrossRetainedConnectionsByCurrentState() {
        GoogleCalendarConnection connected = connection(30L);
        GoogleCalendarConnection syncError = connection(31L);
        syncError.markSyncError("reconnect", Instant.parse("2026-09-01T00:00:00Z"));
        GoogleCalendarRecurrenceEventMapping connectedMapping = master(connected);
        GoogleCalendarRecurrenceEventMapping errorMapping = new GoogleCalendarRecurrenceEventMapping(
                syncError, 40L, "master-old", "etag-old");
        ReflectionTestUtils.setField(errorMapping, "id", 61L);
        when(mappings.listRecurrenceEventMappingsForJob(20L, 40L))
                .thenReturn(List.of(connectedMapping, errorMapping));
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceMasterJobPayload.class)).thenReturn(masterPayload());
        when(client.getEvent("token", "master-1"))
                .thenReturn(Optional.of(provider("master-1", "master-etag", null)));
        when(client.patchRecurrenceEvent("token", "master-1", "master-etag", masterPayload()))
                .thenReturn(provider("master-1", "master-etag-2", null));

        service.execute(job(GoogleCalendarRecurrenceJobKind.MASTER_UPDATE, null), "worker");

        assertThat(connectedMapping.getProviderEtag()).isEqualTo("master-etag-2");
        assertThat(errorMapping.isLocalChanged()).isTrue();
        verify(tokens, never()).getAccessToken(31L);
    }

    @Test
    @DisplayName("CONNECTED master CREATE에 mapping이 없으면 recurring master와 immutable mapping을 만든다")
    void createMasterCreatesProviderAggregateAndMapping() {
        GoogleCalendarConnection connection = connection(30L);
        when(mappings.listRecurrenceEventMappingsForJob(20L, 40L)).thenReturn(List.of());
        when(connections.listConnections(20L)).thenReturn(List.of(connection));
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceMasterJobPayload.class)).thenReturn(masterPayload());
        when(client.insertRecurrenceEvent("token", "provider-id", masterPayload()))
                .thenReturn(provider("master-1", "etag-1", null));

        service.execute(job(GoogleCalendarRecurrenceJobKind.MASTER_CREATE, null), "worker");

        ArgumentCaptor<GoogleCalendarRecurrenceEventMapping> captor =
                ArgumentCaptor.forClass(GoogleCalendarRecurrenceEventMapping.class);
        verify(mappingCommands).createRecurrenceEventMapping(captor.capture());
        assertThat(captor.getValue().getRecurrenceEventId()).isEqualTo(40L);
        assertThat(captor.getValue().getExternalEventId()).isEqualTo("master-1");
    }

    @Test
    @DisplayName("master etag가 바뀌면 aggregate conflict로 격리하고 patch하지 않는다")
    void changedMasterEtagConflictsAggregate() {
        GoogleCalendarConnection connection = connection(30L);
        GoogleCalendarRecurrenceEventMapping mapping = master(connection);
        when(mappings.listRecurrenceEventMappingsForJob(20L, 40L)).thenReturn(List.of(mapping));
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceMasterJobPayload.class)).thenReturn(masterPayload());
        when(client.getEvent("token", "master-1")).thenReturn(Optional.of(provider("master-1", "etag-2", null)));

        service.execute(job(GoogleCalendarRecurrenceJobKind.MASTER_UPDATE, null), "worker");

        assertThat(mapping.isConflicted()).isTrue();
        verify(client, never()).patchRecurrenceEvent(any(), any(), any(), any());
        verify(jobs).recordSyncConflict(50L, 10L, "worker");
    }

    @Test
    @DisplayName("parent mapping이 없는 override는 standalone Event를 만들지 않고 local-only 성공한다")
    void overrideWithoutMasterNeverFallsBackToStandaloneEvent() {
        when(mappings.listRecurrenceEventMappingsForJob(20L, 40L)).thenReturn(List.of());
        when(objectMapper.readValue("payload", GoogleRecurrenceOverrideJobPayload.class))
                .thenReturn(overridePayload());

        service.execute(job(GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT,
                Instant.parse("2026-09-03T00:00:00Z")), "worker");

        verifyNoInteractions(tokens, client, mappingCommands);
        verify(jobs).succeed(50L, 10L, "worker");
    }

    @Test
    @DisplayName("exact override etag 충돌은 parent가 아니라 해당 origin mapping만 격리한다")
    void overrideEtagConflictIsExactOriginOnly() {
        Instant origin = Instant.parse("2026-09-03T00:00:00Z");
        GoogleCalendarConnection connection = connection(30L);
        GoogleCalendarRecurrenceEventMapping master = master(connection);
        GoogleCalendarRecurrenceOverrideMapping override = new GoogleCalendarRecurrenceOverrideMapping(
                master, origin, "instance-1", "override-etag-1");
        ReflectionTestUtils.setField(override, "id", 70L);
        when(mappings.listRecurrenceEventMappingsForJob(20L, 40L)).thenReturn(List.of(master));
        when(mappings.getOverrideMappingIfExists(60L, origin)).thenReturn(Optional.of(override));
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceOverrideJobPayload.class))
                .thenReturn(overridePayload());
        when(client.getEvent("token", "master-1")).thenReturn(Optional.of(provider("master-1", "master-etag", null)));
        when(client.getEvent("token", "instance-1")).thenReturn(Optional.of(provider("instance-1", "override-etag-2", origin)));

        service.execute(job(GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT, origin), "worker");

        assertThat(override.isConflicted()).isTrue();
        assertThat(master.isConflicted()).isFalse();
        verify(client, never()).patchRecurrenceInstance(any(), any(), any(), any());
    }

    @Test
    @DisplayName("active override는 exact origin으로 instance를 resolve해 full snapshot을 적용하고 mapping을 만든다")
    void activeOverrideResolvesExactInstanceAndCreatesMapping() {
        Instant origin = Instant.parse("2026-09-03T00:00:00Z");
        GoogleCalendarConnection connection = connection(30L);
        GoogleCalendarRecurrenceEventMapping master = master(connection);
        when(mappings.listRecurrenceEventMappingsForJob(20L, 40L)).thenReturn(List.of(master));
        when(mappings.getOverrideMappingIfExists(60L, origin)).thenReturn(Optional.empty());
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceOverrideJobPayload.class))
                .thenReturn(overridePayload());
        when(client.getEvent("token", "master-1"))
                .thenReturn(Optional.of(provider("master-1", "master-etag", null)));
        GoogleCalendarEventResponse instance = provider("instance-1", "etag-i", origin);
        when(client.resolveRecurrenceInstance("token", "master-1", origin))
                .thenReturn(Optional.of(instance));
        when(client.patchRecurrenceInstance("token", "instance-1", "etag-i", overridePayload()))
                .thenReturn(provider("instance-1", "etag-i-2", origin));

        service.execute(job(GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT, origin), "worker");

        ArgumentCaptor<GoogleCalendarRecurrenceOverrideMapping> captor =
                ArgumentCaptor.forClass(GoogleCalendarRecurrenceOverrideMapping.class);
        verify(mappingCommands).createOverrideMapping(captor.capture());
        assertThat(captor.getValue().getOriginStartAt()).isEqualTo(origin);
        assertThat(captor.getValue().getExternalEventId()).isEqualTo("instance-1");
    }

    @Test
    @DisplayName("새 exception PATCH의 412도 exact origin mapping으로 격리하고 master를 오염시키지 않는다")
    void newOverridePatchConflictCreatesExactConflictedMapping() {
        Instant origin = Instant.parse("2026-09-03T00:00:00Z");
        GoogleCalendarConnection connection = connection(30L);
        GoogleCalendarRecurrenceEventMapping master = master(connection);
        when(mappings.listRecurrenceEventMappingsForJob(20L, 40L)).thenReturn(List.of(master));
        when(mappings.getOverrideMappingIfExists(60L, origin)).thenReturn(Optional.empty());
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceOverrideJobPayload.class))
                .thenReturn(overridePayload());
        when(client.getEvent("token", "master-1"))
                .thenReturn(Optional.of(provider("master-1", "master-etag", null)));
        GoogleCalendarEventResponse instance = provider("instance-1", "etag-i", origin);
        when(client.resolveRecurrenceInstance("token", "master-1", origin))
                .thenReturn(Optional.of(instance));
        when(client.patchRecurrenceInstance("token", "instance-1", "etag-i", overridePayload()))
                .thenThrow(new com.calio.calendar.external.google.GoogleCalendarEventVersionConflictException(
                        new RuntimeException()));
        when(mappingCommands.createOverrideMapping(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.execute(job(GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT, origin), "worker");

        ArgumentCaptor<GoogleCalendarRecurrenceOverrideMapping> captor =
                ArgumentCaptor.forClass(GoogleCalendarRecurrenceOverrideMapping.class);
        verify(mappingCommands).createOverrideMapping(captor.capture());
        assertThat(captor.getValue().getOriginStartAt()).isEqualTo(origin);
        assertThat(captor.getValue().isConflicted()).isTrue();
        assertThat(master.isConflicted()).isFalse();
    }

    @Test
    @DisplayName("deleted override는 exact mapped instance를 cancel하고 해당 child mapping만 제거한다")
    void deletedOverrideCancelsExactInstanceAndRemovesChildMapping() {
        Instant origin = Instant.parse("2026-09-03T00:00:00Z");
        GoogleCalendarConnection connection = connection(30L);
        GoogleCalendarRecurrenceEventMapping master = master(connection);
        GoogleCalendarRecurrenceOverrideMapping override = new GoogleCalendarRecurrenceOverrideMapping(
                master, origin, "instance-1", "etag-i");
        ReflectionTestUtils.setField(override, "id", 70L);
        when(mappings.listRecurrenceEventMappingsForJob(20L, 40L)).thenReturn(List.of(master));
        when(mappings.getOverrideMappingIfExists(60L, origin)).thenReturn(Optional.of(override));
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(client.getEvent("token", "master-1"))
                .thenReturn(Optional.of(provider("master-1", "master-etag", null)));
        when(client.getEvent("token", "instance-1"))
                .thenReturn(Optional.of(provider("instance-1", "etag-i", origin)));

        service.execute(job(GoogleCalendarRecurrenceJobKind.OVERRIDE_DELETE, origin), "worker");

        verify(client).cancelRecurrenceInstance("token", "instance-1", "etag-i");
        verify(mappingCommands).deleteOverrideMappings(List.of(override));
        assertThat(master.isConflicted()).isFalse();
    }

    @Test
    @DisplayName("connected master delete는 external master만 삭제하고 child와 parent mapping을 함께 제거한다")
    void masterDeleteRemovesAggregateMappingsWithoutChildProviderDeletes() {
        GoogleCalendarConnection connection = connection(30L);
        GoogleCalendarRecurrenceEventMapping master = master(connection);
        when(mappings.listRecurrenceEventMappingsForJob(20L, 40L)).thenReturn(List.of(master));
        when(tokens.getAccessToken(30L)).thenReturn("token");

        when(client.deleteEvent("token", "master-1", "master-etag")).thenReturn(false);

        service.execute(job(GoogleCalendarRecurrenceJobKind.MASTER_DELETE, null), "worker");

        verify(client).deleteEvent("token", "master-1", "master-etag");
        verify(mappingCommands).deleteRecurrenceAggregateMappings(master);
        verify(client, never()).cancelRecurrenceInstance(any(), any(), any());
    }

    @Test
    @DisplayName("disconnected master delete는 parent와 child identity를 제거하지 않고 master local branch만 기록한다")
    void disconnectedMasterDeleteRetainsAggregateIdentity() {
        GoogleCalendarConnection connection = connection(30L);
        connection.disconnect(Instant.parse("2026-09-01T00:00:00Z"));
        GoogleCalendarRecurrenceEventMapping master = master(connection);
        when(mappings.listRecurrenceEventMappingsForJob(20L, 40L)).thenReturn(List.of(master));

        service.execute(job(GoogleCalendarRecurrenceJobKind.MASTER_DELETE, null), "worker");

        assertThat(master.isLocalChanged()).isTrue();
        verifyNoInteractions(tokens, client);
        verify(mappingCommands, never()).deleteRecurrenceAggregateMappings(any());
    }

    private GoogleCalendarRecurrenceJob job(GoogleCalendarRecurrenceJobKind kind, Instant origin) {
        GoogleCalendarRecurrenceJob job = GoogleCalendarRecurrenceJob.create(
                "operation", 20L, 10L, 1L, kind, 40L, origin, "payload",
                kind == GoogleCalendarRecurrenceJobKind.MASTER_CREATE ? "provider-id" : null,
                Instant.parse("2026-09-01T00:00:00Z"));
        ReflectionTestUtils.setField(job, "id", 50L);
        return job;
    }

    private GoogleCalendarConnection connection(Long id) {
        GoogleCalendarIntegration integration = new GoogleCalendarIntegration(10L);
        ReflectionTestUtils.setField(integration, "id", 20L);
        GoogleCalendarConnection connection = new GoogleCalendarConnection(integration, "subject",
                "user@example.com", "refresh", "access", Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"));
        ReflectionTestUtils.setField(connection, "id", id);
        return connection;
    }

    private GoogleCalendarRecurrenceEventMapping master(GoogleCalendarConnection connection) {
        GoogleCalendarRecurrenceEventMapping mapping = new GoogleCalendarRecurrenceEventMapping(
                connection, 40L, "master-1", "master-etag");
        ReflectionTestUtils.setField(mapping, "id", 60L);
        return mapping;
    }

    private GoogleRecurrenceMasterJobPayload masterPayload() {
        return new GoogleRecurrenceMasterJobPayload("title", null,
                Instant.parse("2026-09-03T00:00:00Z"), Instant.parse("2026-09-03T01:00:00Z"),
                false, "UTC", List.of("RRULE:FREQ=DAILY"));
    }

    private GoogleRecurrenceOverrideJobPayload overridePayload() {
        return new GoogleRecurrenceOverrideJobPayload("moved", null,
                Instant.parse("2026-09-03T02:00:00Z"), Instant.parse("2026-09-03T03:00:00Z"),
                false, "UTC");
    }

    private GoogleCalendarEventResponse provider(String id, String etag, Instant origin) {
        GoogleCalendarEventTimeResponse start = new GoogleCalendarEventTimeResponse(
                null, "2026-09-03T00:00:00Z", "UTC");
        return new GoogleCalendarEventResponse(id, "confirmed", etag,
                Instant.parse("2026-09-03T00:00:00Z"), "title", null, List.of(),
                origin == null ? null : "master-1",
                origin == null ? null : new GoogleCalendarEventTimeResponse(null, origin.toString(), "UTC"),
                start, new GoogleCalendarEventTimeResponse(null, "2026-09-03T01:00:00Z", "UTC"));
    }
}
