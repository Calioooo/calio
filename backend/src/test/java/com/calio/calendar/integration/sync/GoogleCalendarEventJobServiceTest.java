package com.calio.calendar.integration.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.external.google.GoogleCalendarEventsClient;
import com.calio.calendar.external.google.dto.GoogleCalendarEventResponse;
import com.calio.calendar.integration.connection.domain.GoogleCalendarConnection;
import com.calio.calendar.integration.connection.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.connection.service.GoogleCalendarAccessTokenService;
import com.calio.calendar.integration.connection.service.GoogleCalendarConnectionQueryService;
import com.calio.calendar.integration.mapping.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingCommandService;
import com.calio.calendar.integration.mapping.service.GoogleCalendarEventMappingQueryService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarEventJob;
import com.calio.calendar.integration.sync.operation.domain.GoogleOperationJobKind;
import com.calio.calendar.integration.sync.operation.dto.GoogleEventJobPayload;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class GoogleCalendarEventJobServiceTest {

    private final GoogleCalendarConnectionQueryService connectionQueryService = mock();
    private final GoogleCalendarEventMappingQueryService mappingQueryService = mock();
    private final GoogleCalendarEventMappingCommandService mappingCommandService = mock();
    private final GoogleCalendarAccessTokenService accessTokenService = mock();
    private final GoogleCalendarEventsClient eventsClient = mock();
    private final ObjectMapper objectMapper = mock();
    private final GoogleOperationJobService jobService = mock();
    private final TransactionTemplate jobTransaction = mock();
    private final AtomicBoolean transactionActive = new AtomicBoolean();
    private GoogleCalendarEventJobService service;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            transactionActive.set(true);
            try {
                return invocation.getArgument(0, TransactionCallback.class).doInTransaction(null);
            } finally {
                transactionActive.set(false);
            }
        })
                .when(jobTransaction).execute(any());
        lenient().doAnswer(invocation -> {
            transactionActive.set(true);
            try {
                invocation.getArgument(0, Consumer.class).accept(null);
                return null;
            } finally {
                transactionActive.set(false);
            }
        }).when(jobTransaction).executeWithoutResult(any());
        service = new GoogleCalendarEventJobService(
                connectionQueryService, mappingQueryService, mappingCommandService,
                accessTokenService, eventsClient, objectMapper, jobService, jobTransaction);
    }

    @Test
    @DisplayName("연결이 끊긴 mapping의 Event 변경은 Google 호출 없이 localChanged로 기록한다")
    void givenDisconnectedMapping_whenApplyUpdate_thenRecordsLocalChangeOnly() {
        // given
        GoogleCalendarConnection connection = connection(30L);
        connection.disconnect(Instant.parse("2026-09-01T00:00:00Z"));
        GoogleCalendarEventMapping mapping = new GoogleCalendarEventMapping(
                connection, 40L, "external-1", "etag-1");
        GoogleCalendarEventJob job = job(GoogleOperationJobKind.UPDATE);
        when(mappingQueryService.listEventMappingsForEvent(20L, 40L))
                .thenReturn(List.of(mapping));

        // when
        service.apply(job, "worker");

        // then
        assertThat(mapping.isLocalChanged()).isTrue();
        verifyNoInteractions(accessTokenService, eventsClient);
        verify(jobService).succeed(50L, 10L, "worker");
    }

    @Test
    @DisplayName("연결된 mapping의 etag가 달라지면 Google patch 없이 conflict를 보존한다")
    void givenChangedProviderEtag_whenApplyUpdate_thenMarksConflictWithoutPatch() {
        // given
        GoogleCalendarConnection connection = connection(30L);
        GoogleCalendarEventMapping mapping = new GoogleCalendarEventMapping(
                connection, 40L, "external-1", "etag-1");
        GoogleCalendarEventJob job = job(GoogleOperationJobKind.UPDATE);
        when(mappingQueryService.listEventMappingsForEvent(20L, 40L))
                .thenReturn(List.of(mapping));
        when(accessTokenService.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleEventJobPayload.class)).thenReturn(payload());
        when(eventsClient.getEvent("token", "external-1"))
                .thenReturn(Optional.of(providerEvent("etag-2")));

        // when
        service.apply(job, "worker");

        // then
        assertThat(mapping.isConflicted()).isTrue();
        verify(eventsClient, never()).patchEvent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        verify(jobService).recordSyncConflict(50L, 10L, "worker");
        verify(jobService).completeSyncRun(50L, 10L, "worker");
    }

    @Test
    @DisplayName("CREATE 중 provider conflict가 감지되면 새 Google Event를 생성하지 않는다")
    void givenProviderConflict_whenApplyCreate_thenDoesNotInsertGoogleEvent() {
        // given
        GoogleCalendarConnection connection = connection(30L);
        GoogleCalendarEventMapping mapping = new GoogleCalendarEventMapping(
                connection, 40L, "external-1", "etag-1");
        GoogleCalendarEventJob job = job(GoogleOperationJobKind.CREATE);
        when(mappingQueryService.listEventMappingsForEvent(20L, 40L))
                .thenReturn(List.of(mapping));
        when(accessTokenService.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleEventJobPayload.class)).thenReturn(payload());
        when(eventsClient.getEvent("token", "external-1"))
                .thenReturn(Optional.of(providerEvent("etag-2")));

        // when
        service.apply(job, "worker");

        // then
        verify(eventsClient, never()).insertEvent(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(mappingCommandService, never()).createEventMapping(
                org.mockito.ArgumentMatchers.any(GoogleCalendarEventMapping.class));
        verify(jobService).completeSyncRun(50L, 10L, "worker");
    }

    @Test
    @DisplayName("연결된 mapping의 DELETE Job은 Google Event를 삭제하고 성공 처리한다")
    void givenConnectedMapping_whenApplyDelete_thenDeletesGoogleEventAndSucceeds() {
        // given
        GoogleCalendarConnection connection = connection(30L);
        GoogleCalendarEventMapping mapping = new GoogleCalendarEventMapping(
                connection, 40L, "external-1", "etag-1");
        GoogleCalendarEventJob job = job(GoogleOperationJobKind.DELETE);
        when(mappingQueryService.listEventMappingsForEvent(20L, 40L))
                .thenReturn(List.of(mapping));
        when(accessTokenService.getAccessToken(30L)).thenReturn("token");

        // when
        service.apply(job, "worker");

        // then
        verify(eventsClient).deleteEvent("token", "external-1");
        verifyNoInteractions(objectMapper);
        verify(jobService).succeed(50L, 10L, "worker");
    }

    @Test
    @DisplayName("삭제된 Event의 CREATE Job도 snapshot으로 Google Event와 durable mapping을 생성한다")
    void givenDeletedEvent_whenApplyCreate_thenCreatesGoogleEventWithoutEventLookup() {
        // given
        GoogleCalendarConnection connection = connection(30L);
        GoogleCalendarEventJob job = job(GoogleOperationJobKind.CREATE);
        when(mappingQueryService.listEventMappingsForEvent(20L, 40L)).thenReturn(List.of());
        when(connectionQueryService.listConnections(20L)).thenReturn(List.of(connection));
        when(accessTokenService.getAccessToken(30L)).thenReturn("token");
        when(objectMapper.readValue("payload", GoogleEventJobPayload.class)).thenReturn(payload());
        when(eventsClient.insertEvent(
                org.mockito.ArgumentMatchers.eq("token"), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    assertThat(transactionActive).isFalse();
                    return providerEvent("etag-created");
                });

        // when
        service.apply(job, "worker");

        // then
        ArgumentCaptor<GoogleCalendarEventMapping> mappingCaptor =
                ArgumentCaptor.forClass(GoogleCalendarEventMapping.class);
        ArgumentCaptor<com.calio.calendar.external.google.dto.GoogleCalendarEventWriteRequest>
                writeRequestCaptor = ArgumentCaptor.forClass(
                        com.calio.calendar.external.google.dto.GoogleCalendarEventWriteRequest.class);
        verify(eventsClient).insertEvent(org.mockito.ArgumentMatchers.eq("token"), writeRequestCaptor.capture());
        assertThat(writeRequestCaptor.getValue().id()).isEqualTo(providerIdentity(GoogleOperationJobKind.CREATE));
        verify(mappingCommandService).createEventMapping(mappingCaptor.capture());
        assertThat(mappingCaptor.getValue().getEventId()).isEqualTo(40L);
        assertThat(mappingCaptor.getValue().getExternalEventId()).isEqualTo("external-1");
        verify(jobService).succeed(50L, 10L, "worker");
    }

    private GoogleCalendarEventJob job(GoogleOperationJobKind kind) {
        GoogleCalendarEventJob job = GoogleCalendarEventJob.create(
                "operation", 20L, 10L, 1L, kind, 40L, providerIdentity(kind),
                "payload", Instant.parse("2026-09-03T00:00:00Z"));
        ReflectionTestUtils.setField(job, "id", 50L);
        return job;
    }

    private GoogleCalendarConnection connection(Long id) {
        GoogleCalendarIntegration integration = new GoogleCalendarIntegration(10L);
        ReflectionTestUtils.setField(integration, "id", 20L);
        GoogleCalendarConnection connection = new GoogleCalendarConnection(
                integration, "subject", "user@example.com", "refresh", "access",
                Instant.parse("2027-01-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));
        ReflectionTestUtils.setField(connection, "id", id);
        return connection;
    }

    private String providerIdentity(GoogleOperationJobKind kind) {
        return kind == GoogleOperationJobKind.CREATE ? "c10000000000000014000000000000028" : null;
    }

    private GoogleEventJobPayload payload() {
        return new GoogleEventJobPayload(
                "title", null, Instant.parse("2026-09-03T00:00:00Z"),
                Instant.parse("2026-09-03T01:00:00Z"), false, "UTC");
    }

    private GoogleCalendarEventResponse providerEvent(String etag) {
        return new GoogleCalendarEventResponse(
                "external-1", "confirmed", etag, Instant.parse("2026-09-03T00:00:00Z"),
                "title", null, List.of(), null,
                new com.calio.calendar.external.google.dto.GoogleCalendarEventTimeResponse(
                        null, "2026-09-03T00:00:00Z", "UTC"),
                new com.calio.calendar.external.google.dto.GoogleCalendarEventTimeResponse(
                        null, "2026-09-03T01:00:00Z", "UTC"));
    }
}
