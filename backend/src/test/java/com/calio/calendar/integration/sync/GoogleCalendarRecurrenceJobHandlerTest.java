package com.calio.calendar.integration.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.external.google.GoogleCalendarEventVersionConflictException;
import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventTimeResponse;
import com.calio.calendar.external.google.dto.GoogleCalendarEventWriteRequest;
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
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class GoogleCalendarRecurrenceJobHandlerTest {
    private final GoogleCalendarConnectionQueryService connections = mock();
    private final GoogleCalendarRecurrenceMappingQueryService mappings = mock();
    private final GoogleCalendarRecurrenceMappingCommandService mappingCommands = mock();
    private final GoogleCalendarAccessTokenService tokens = mock();
    private final GoogleCalendarEventsClient client = mock();
    private final ObjectMapper objectMapper = mock();
    private final GoogleOperationJobService jobs = mock();
    private final TransactionTemplate transaction = mock();
    private GoogleCalendarRecurrenceJobHandler handler;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> invocation.getArgument(0, TransactionCallback.class)
                .doInTransaction(null)).when(transaction).execute(any());
        lenient().doAnswer(invocation -> {
            invocation.getArgument(0, Consumer.class).accept(null);
            return null;
        }).when(transaction).executeWithoutResult(any());
        handler = handlerWith(objectMapper);
    }

    @Test
    @DisplayName("CONNECTED Connection이 없으면 inactive recurrence-event local branch만 기록한다")
    void updateWithoutConnectedConnectionMarksInactiveMappings() {
        when(objectMapper.readValue("payload", GoogleRecurrenceJobPayload.class))
                .thenReturn(recurrenceEventPayload());

        handler.execute(job(GoogleCalendarRecurrenceJobKind.RECURRENCE_UPDATE, null), "worker");

        verify(mappingCommands).markInactiveRecurrenceEventMappingsLocalChanged(20L, 40L);
        verifyNoInteractions(tokens, client);
        verify(jobs).succeed(50L, 10L, "worker");
    }

    @Test
    @DisplayName("recurrence-event UPDATE는 CONNECTED mapping 하나만 provider에 반영한다")
    void updateWritesOnlyConnectedMapping() {
        GoogleCalendarRecurrenceEventMapping mapping = recurrenceEventMapping(connection(30L));
        givenConnectedRecurrenceEvent(mapping);
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceJobPayload.class))
                .thenReturn(recurrenceEventPayload());
        when(client.getEvent("token", "recurrence-event-1"))
                .thenReturn(Optional.of(provider("recurrence-event-1", "recurrence-event-etag", null)));
        when(client.patch("token", "recurrence-event-1", "recurrence-event-etag",
                GoogleCalendarEventWriteRequest.forRecurrenceUpdate(recurrenceEventPayload())))
                .thenReturn(provider("recurrence-event-1", "recurrence-event-etag-2", null));

        handler.execute(job(GoogleCalendarRecurrenceJobKind.RECURRENCE_UPDATE, null), "worker");

        assertThat(mapping.getProviderEtag()).isEqualTo("recurrence-event-etag-2");
        verify(mappingCommands).markInactiveRecurrenceEventMappingsLocalChanged(20L, 40L);
        verify(jobs).succeed(50L, 10L, "worker");
    }

    @Test
    @DisplayName("CONNECTED recurrence-event CREATE에 mapping이 없으면 provider recurrence-event와 mapping을 만든다")
    void createRecurrenceEventCreatesProviderAggregateAndMapping() {
        GoogleCalendarConnection connection = connection(30L);
        givenConnectedConnectionWithoutMapping(connection);
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceJobPayload.class))
                .thenReturn(recurrenceEventPayload());
        when(client.post("token", GoogleCalendarEventWriteRequest.forRecurrenceCreate(
                recurrenceEventPayload(), "provider-id")))
                .thenReturn(provider("recurrence-event-1", "etag-1", null));

        handler.execute(job(GoogleCalendarRecurrenceJobKind.RECURRENCE_CREATE, null), "worker");

        ArgumentCaptor<GoogleCalendarRecurrenceEventMapping> captor =
                ArgumentCaptor.forClass(GoogleCalendarRecurrenceEventMapping.class);
        verify(mappingCommands).createRecurrenceEventMapping(captor.capture());
        assertThat(captor.getValue().getConnection()).isSameAs(connection);
        assertThat(captor.getValue().getExternalEventId()).isEqualTo("recurrence-event-1");
    }

    @Test
    @DisplayName("CREATE에 active mapping이 이미 있으면 provider write 없이 완료한다")
    void createWithExistingMappingCompletesIdempotently() {
        GoogleCalendarRecurrenceEventMapping mapping = recurrenceEventMapping(connection(30L));
        givenConnectedRecurrenceEvent(mapping);
        when(objectMapper.readValue("payload", GoogleRecurrenceJobPayload.class))
                .thenReturn(recurrenceEventPayload());

        handler.execute(job(GoogleCalendarRecurrenceJobKind.RECURRENCE_CREATE, null), "worker");

        verifyNoInteractions(tokens, client);
        verify(mappingCommands, never()).createRecurrenceEventMapping(any());
        verify(jobs).succeed(50L, 10L, "worker");
    }

    @Test
    @DisplayName("CREATE에 conflicted active mapping이 이미 있으면 provider write 없이 skip한다")
    void createWithConflictedMappingSkipsProviderWrite() {
        GoogleCalendarRecurrenceEventMapping mapping = recurrenceEventMapping(connection(30L));
        mapping.markConflicted();
        givenConnectedRecurrenceEvent(mapping);
        when(objectMapper.readValue("payload", GoogleRecurrenceJobPayload.class))
                .thenReturn(recurrenceEventPayload());

        handler.execute(job(GoogleCalendarRecurrenceJobKind.RECURRENCE_CREATE, null), "worker");

        verifyNoInteractions(tokens, client);
        verify(mappingCommands, never()).createRecurrenceEventMapping(any());
        verify(jobs).skipConflictedScope(50L, 10L, "worker");
    }

    @Test
    @DisplayName("recurrence-event etag가 바뀌면 active mapping만 aggregate conflict로 격리한다")
    void changedRecurrenceEventEtagConflictsAggregate() {
        GoogleCalendarRecurrenceEventMapping mapping = recurrenceEventMapping(connection(30L));
        givenConnectedRecurrenceEvent(mapping);
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceJobPayload.class))
                .thenReturn(recurrenceEventPayload());
        when(client.getEvent("token", "recurrence-event-1"))
                .thenReturn(Optional.of(provider("recurrence-event-1", "etag-2", null)));

        handler.execute(job(GoogleCalendarRecurrenceJobKind.RECURRENCE_UPDATE, null), "worker");

        assertThat(mapping.isConflicted()).isTrue();
        verify(client, never()).patch(any(), any(), any(), any());
        verify(jobs).completeWithConflict(50L, 10L, "worker");
    }

    @Test
    @DisplayName("이미 conflicted인 active recurrence-event는 provider 작업 없이 skip한다")
    void alreadyConflictedRecurrenceEventSkipsProviderWrite() {
        GoogleCalendarRecurrenceEventMapping mapping = recurrenceEventMapping(connection(30L));
        mapping.markConflicted();
        givenConnectedRecurrenceEvent(mapping);
        when(objectMapper.readValue("payload", GoogleRecurrenceJobPayload.class))
                .thenReturn(recurrenceEventPayload());

        handler.execute(job(GoogleCalendarRecurrenceJobKind.RECURRENCE_UPDATE, null), "worker");

        verifyNoInteractions(tokens, client);
        verify(jobs).skipConflictedScope(50L, 10L, "worker");
    }

    @Test
    @DisplayName("CONNECTED parent mapping이 없는 override는 standalone Event를 만들지 않는다")
    void overrideWithoutRecurrenceEventNeverFallsBackToStandaloneEvent() {
        givenConnectedConnectionWithoutMapping(connection(30L));
        when(objectMapper.readValue("payload", GoogleRecurrenceOverrideJobPayload.class))
                .thenReturn(overridePayload());

        handler.execute(job(GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT, origin()), "worker");

        verifyNoInteractions(tokens, client);
        verify(mappingCommands).markInactiveOverrideMappingsLocalChanged(20L, 40L, origin());
        verify(jobs).succeed(50L, 10L, "worker");
    }

    @Test
    @DisplayName("exact override etag 충돌은 해당 origin mapping만 격리한다")
    void overrideEtagConflictIsExactOriginOnly() {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = recurrenceEventMapping(connection(30L));
        GoogleCalendarRecurrenceOverrideMapping override = override(recurrenceEventMapping, "override-etag-1");
        givenConnectedOverride(recurrenceEventMapping, override);
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceOverrideJobPayload.class))
                .thenReturn(overridePayload());
        when(client.getEvent("token", "recurrence-event-1"))
                .thenReturn(Optional.of(provider("recurrence-event-1", "recurrence-event-etag", null)));
        when(client.getEvent("token", "occurrence-1"))
                .thenReturn(Optional.of(provider("occurrence-1", "override-etag-2", origin())));

        handler.execute(job(GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT, origin()), "worker");

        assertThat(override.isConflicted()).isTrue();
        assertThat(recurrenceEventMapping.isConflicted()).isFalse();
        verify(client, never()).patch(any(), any(), any(), any());
    }

    @Test
    @DisplayName("mapping 없는 active override는 exact occurrence를 수정하고 mapping을 만든다")
    void activeOverrideCreatesMapping() {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = recurrenceEventMapping(connection(30L));
        givenConnectedOverride(recurrenceEventMapping, null);
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceOverrideJobPayload.class))
                .thenReturn(overridePayload());
        when(client.getEvent("token", "recurrence-event-1"))
                .thenReturn(Optional.of(provider("recurrence-event-1", "recurrence-event-etag", null)));
        when(client.getRecurrenceOccurrence("token", "recurrence-event-1", origin()))
                .thenReturn(Optional.of(provider("occurrence-1", "etag-i", origin())));
        when(client.patch("token", "occurrence-1", "etag-i",
                GoogleCalendarEventWriteRequest.forOverrideUpdate(overridePayload())))
                .thenReturn(provider("occurrence-1", "etag-i-2", origin()));

        handler.execute(job(GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT, origin()), "worker");

        ArgumentCaptor<GoogleCalendarRecurrenceOverrideMapping> captor =
                ArgumentCaptor.forClass(GoogleCalendarRecurrenceOverrideMapping.class);
        verify(mappingCommands).createOverrideMapping(captor.capture());
        assertThat(captor.getValue().getExternalEventId()).isEqualTo("occurrence-1");
        assertThat(captor.getValue().getOriginStartAt()).isEqualTo(origin());
    }

    @Test
    @DisplayName("취소된 unmapped occurrence는 PATCH하지 않고 recurrence-event conflict로 종료한다")
    void cancelledUnmappedOccurrenceConflictsRecurrenceEvent() {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = recurrenceEventMapping(connection(30L));
        givenConnectedOverride(recurrenceEventMapping, null);
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceOverrideJobPayload.class))
                .thenReturn(overridePayload());
        when(client.getEvent("token", "recurrence-event-1"))
                .thenReturn(Optional.of(provider("recurrence-event-1", "recurrence-event-etag", null)));
        when(client.getRecurrenceOccurrence("token", "recurrence-event-1", origin()))
                .thenReturn(Optional.of(cancelledProviderOccurrence("occurrence-1")));

        handler.execute(job(GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT, origin()), "worker");

        assertThat(recurrenceEventMapping.isConflicted()).isTrue();
        verify(client, never()).patch(any(), any(), any(), any());
        verify(jobs).completeWithConflict(50L, 10L, "worker");
    }

    @Test
    @DisplayName("새 occurrence PATCH의 412는 exact origin mapping을 생성해 격리한다")
    void newOverridePatchConflictCreatesConflictedMapping() {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = recurrenceEventMapping(connection(30L));
        givenConnectedOverride(recurrenceEventMapping, null);
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleRecurrenceOverrideJobPayload.class))
                .thenReturn(overridePayload());
        when(client.getEvent("token", "recurrence-event-1"))
                .thenReturn(Optional.of(provider("recurrence-event-1", "recurrence-event-etag", null)));
        when(client.getRecurrenceOccurrence("token", "recurrence-event-1", origin()))
                .thenReturn(Optional.of(provider("occurrence-1", "etag-i", origin())));
        when(client.patch("token", "occurrence-1", "etag-i",
                GoogleCalendarEventWriteRequest.forOverrideUpdate(overridePayload())))
                .thenThrow(new GoogleCalendarEventVersionConflictException(new RuntimeException()));
        when(mappingCommands.createOverrideMapping(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        handler.execute(job(GoogleCalendarRecurrenceJobKind.OVERRIDE_UPSERT, origin()), "worker");

        ArgumentCaptor<GoogleCalendarRecurrenceOverrideMapping> captor =
                ArgumentCaptor.forClass(GoogleCalendarRecurrenceOverrideMapping.class);
        verify(mappingCommands).createOverrideMapping(captor.capture());
        assertThat(captor.getValue().isConflicted()).isTrue();
        assertThat(recurrenceEventMapping.isConflicted()).isFalse();
    }

    @Test
    @DisplayName("deleted override는 mapped occurrence를 cancel하고 child mapping만 제거한다")
    void deletedOverrideRemovesMapping() {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = recurrenceEventMapping(connection(30L));
        GoogleCalendarRecurrenceOverrideMapping override = override(recurrenceEventMapping, "etag-i");
        givenConnectedOverride(recurrenceEventMapping, override);
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(client.getEvent("token", "recurrence-event-1"))
                .thenReturn(Optional.of(provider("recurrence-event-1", "recurrence-event-etag", null)));
        when(client.getEvent("token", "occurrence-1"))
                .thenReturn(Optional.of(provider("occurrence-1", "etag-i", origin())));

        handler.execute(job(GoogleCalendarRecurrenceJobKind.OVERRIDE_DELETE, origin()), "worker");

        verify(client).delete("token", "occurrence-1", "etag-i");
        verify(mappingCommands).deleteOverrideMappings(List.of(override));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("provider recurrence-event가 삭제되었거나 이미 없으면 active aggregate mapping을 제거한다")
    void deletedProviderRecurrenceEventRemovesAggregateMapping(boolean providerDeleted) {
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping = recurrenceEventMapping(connection(30L));
        givenConnectedRecurrenceEvent(recurrenceEventMapping);
        when(tokens.getAccessToken(30L)).thenReturn("token");
        when(client.delete("token", "recurrence-event-1", "recurrence-event-etag")).thenReturn(providerDeleted);

        handler.execute(job(GoogleCalendarRecurrenceJobKind.RECURRENCE_DELETE, null), "worker");

        verify(mappingCommands).deleteRecurrenceAggregateMappings(recurrenceEventMapping);
        verify(mappingCommands).markInactiveRecurrenceEventMappingsLocalChanged(20L, 40L);
    }

    @Test
    @DisplayName("CONNECTED recurrence-event가 없는 DELETE는 inactive identity만 localChanged로 남긴다")
    void deleteWithoutConnectedRecurrenceEventRetainsInactiveIdentity() {
        handler.execute(job(GoogleCalendarRecurrenceJobKind.RECURRENCE_DELETE, null), "worker");

        verify(mappingCommands).markInactiveRecurrenceEventMappingsLocalChanged(20L, 40L);
        verify(mappingCommands, never()).deleteRecurrenceAggregateMappings(any());
        verifyNoInteractions(tokens, client);
    }

    @Test
    @DisplayName("null recurrence payload는 invalid request로 종료한다")
    void nullRecurrencePayloadIsRejected() {
        GoogleCalendarRecurrenceJob job = job(GoogleCalendarRecurrenceJobKind.RECURRENCE_UPDATE, null);
        ReflectionTestUtils.setField(job, "targetPayload", "null");

        assertThatThrownBy(() -> handlerWith(new ObjectMapper()).execute(job, "worker"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID));
        verifyNoInteractions(tokens, client);
    }

    @Test
    @DisplayName("recurrence가 누락된 recurrence-event payload는 invalid request로 종료한다")
    void missingRecurrencePayloadIsRejected() {
        GoogleCalendarRecurrenceJob job = job(GoogleCalendarRecurrenceJobKind.RECURRENCE_UPDATE, null);
        ReflectionTestUtils.setField(job, "targetPayload", "{\"title\":\"daily\"}");

        assertThatThrownBy(() -> handlerWith(new ObjectMapper()).execute(job, "worker"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_REQUEST_INVALID));
        verifyNoInteractions(tokens, client);
    }

    private void givenConnectedConnectionWithoutMapping(GoogleCalendarConnection connection) {
        when(connections.getConnectedConnectionByIntegrationIdIfExists(20L))
                .thenReturn(Optional.of(connection));
        when(connections.getConnectionIfExists(connection.getId())).thenReturn(Optional.of(connection));
        when(mappings.getRecurrenceEventMappingIfExists(connection.getId(), 40L))
                .thenReturn(Optional.empty());
    }

    private void givenConnectedRecurrenceEvent(GoogleCalendarRecurrenceEventMapping recurrenceEventMapping) {
        GoogleCalendarConnection connection = recurrenceEventMapping.getConnection();
        when(connections.getConnectedConnectionByIntegrationIdIfExists(20L))
                .thenReturn(Optional.of(connection));
        when(mappings.getRecurrenceEventMappingIfExists(connection.getId(), 40L))
                .thenReturn(Optional.of(recurrenceEventMapping));
        when(mappings.getRecurrenceEventMappingIfExists(recurrenceEventMapping.getId()))
                .thenReturn(Optional.of(recurrenceEventMapping));
    }

    private void givenConnectedOverride(
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping,
            GoogleCalendarRecurrenceOverrideMapping override
    ) {
        givenConnectedRecurrenceEvent(recurrenceEventMapping);
        when(mappings.getOverrideMappingIfExists(recurrenceEventMapping.getId(), origin()))
                .thenReturn(Optional.ofNullable(override));
    }

    private GoogleCalendarRecurrenceJobHandler handlerWith(ObjectMapper mapper) {
        return new GoogleCalendarRecurrenceJobHandler(connections, mappings, mappingCommands,
                tokens, client, mapper, jobs, transaction);
    }

    private GoogleCalendarRecurrenceJob job(GoogleCalendarRecurrenceJobKind kind, Instant origin) {
        GoogleCalendarRecurrenceJob job = GoogleCalendarRecurrenceJob.create(
                "operation", 20L, 10L, 1L, kind, 40L, origin, "payload",
                kind == GoogleCalendarRecurrenceJobKind.RECURRENCE_CREATE ? "provider-id" : null,
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

    private GoogleCalendarRecurrenceEventMapping recurrenceEventMapping(GoogleCalendarConnection connection) {
        GoogleCalendarRecurrenceEventMapping mapping = new GoogleCalendarRecurrenceEventMapping(
                connection, 40L, "recurrence-event-1", "recurrence-event-etag");
        ReflectionTestUtils.setField(mapping, "id", 60L);
        return mapping;
    }

    private GoogleCalendarRecurrenceOverrideMapping override(
            GoogleCalendarRecurrenceEventMapping recurrenceEventMapping,
            String etag
    ) {
        GoogleCalendarRecurrenceOverrideMapping mapping =
                new GoogleCalendarRecurrenceOverrideMapping(recurrenceEventMapping, origin(), "occurrence-1", etag);
        ReflectionTestUtils.setField(mapping, "id", 70L);
        return mapping;
    }

    private GoogleRecurrenceJobPayload recurrenceEventPayload() {
        return new GoogleRecurrenceJobPayload("title", null,
                Instant.parse("2026-09-03T00:00:00Z"), Instant.parse("2026-09-03T01:00:00Z"),
                false, "UTC", List.of("RRULE:FREQ=DAILY"));
    }

    private GoogleRecurrenceOverrideJobPayload overridePayload() {
        return new GoogleRecurrenceOverrideJobPayload("moved", null,
                Instant.parse("2026-09-03T02:00:00Z"), Instant.parse("2026-09-03T03:00:00Z"),
                false, "UTC");
    }

    private Instant origin() {
        return Instant.parse("2026-09-03T00:00:00Z");
    }

    private GoogleCalendarEventResponse provider(String id, String etag, Instant origin) {
        GoogleCalendarEventTimeResponse start = new GoogleCalendarEventTimeResponse(
                null, "2026-09-03T00:00:00Z", "UTC");
        return new GoogleCalendarEventResponse(id, "confirmed", etag,
                Instant.parse("2026-09-03T00:00:00Z"), "title", null, List.of(),
                origin == null ? null : "recurrence-event-1",
                origin == null ? null : new GoogleCalendarEventTimeResponse(null, origin.toString(), "UTC"),
                start, new GoogleCalendarEventTimeResponse(null, "2026-09-03T01:00:00Z", "UTC"));
    }

    private GoogleCalendarEventResponse cancelledProviderOccurrence(String id) {
        return new GoogleCalendarEventResponse(id, "cancelled", null,
                Instant.parse("2026-09-03T00:00:00Z"), null, null, List.of(), "recurrence-event-1",
                new GoogleCalendarEventTimeResponse(null, origin().toString(), "UTC"), null, null);
    }
}
