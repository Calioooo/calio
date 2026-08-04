package com.calio.calendar.integration.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarMappingSyncStatus;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Pageable;

class GoogleCalendarProviderDataServiceTest {

    @Test
    @DisplayName("conflicted recurrence-event의 unseen child override는 삭제하지 않는다")
    void givenConflictedRecurrenceEvent_whenOverrideIsUnseen_thenPreservesOverride() {
        // given
        GoogleCalendarIntegrationRepository integrationRepository =
                mock(GoogleCalendarIntegrationRepository.class);
        GoogleCalendarEventMappingRepository eventMappingRepository =
                mock(GoogleCalendarEventMappingRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository =
                mock(GoogleCalendarRecurrenceEventMappingRepository.class);
        GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository =
                mock(GoogleCalendarRecurrenceOverrideMappingRepository.class);
        RecurrenceEventRepository recurrenceEventRepository =
                mock(RecurrenceEventRepository.class);
        RecurrenceEventOverrideRepository overrideRepository =
                mock(RecurrenceEventOverrideRepository.class);
        GoogleOperationJobPersistenceService jobPersistenceService =
                mock(GoogleOperationJobPersistenceService.class);
        GoogleCalendarRecurrenceEventMapping recurrenceEventMapping =
                mock(GoogleCalendarRecurrenceEventMapping.class);
        GoogleCalendarRecurrenceOverrideMapping overrideMapping =
                mock(GoogleCalendarRecurrenceOverrideMapping.class);
        when(recurrenceEventMapping.getSyncStatus())
                .thenReturn(GoogleCalendarMappingSyncStatus.CONFLICTED);
        when(overrideMapping.getId()).thenReturn(11L);
        when(overrideMapping.getExternalEventId()).thenReturn("override-1");
        when(overrideMapping.getRecurrenceEventMapping()).thenReturn(recurrenceEventMapping);
        when(overrideMappingRepository
                .findNextBatchWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationIdAfterIdForUpdate(
                        eq(1L), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(overrideMapping));
        when(overrideMappingRepository
                .findNextBatchWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationIdAfterIdForUpdate(
                        eq(1L), eq(11L), any(Pageable.class)))
                .thenReturn(List.of());
        when(recurrenceMappingRepository
                .findNextBatchWithRecurrenceEventByIntegrationIdAfterIdForUpdate(
                        eq(1L), eq(0L), any(Pageable.class)))
                .thenReturn(List.of());
        when(eventMappingRepository.findNextBatchWithEventByIntegrationIdAfterIdForUpdate(
                eq(1L), eq(0L), any(Pageable.class))).thenReturn(List.of());
        when(integrationRepository.extendSyncLease(1L, "run-1")).thenReturn(1);
        when(integrationRepository.finalizeSync(1L, "run-1", "next-token")).thenReturn(1);
        GoogleCalendarProviderDataService service = new GoogleCalendarProviderDataService(
                integrationRepository,
                eventMappingRepository,
                eventRepository,
                recurrenceMappingRepository,
                overrideMappingRepository,
                recurrenceEventRepository,
                overrideRepository,
                null,
                jobPersistenceService,
                new GoogleCalendarInboundConflictService(
                        mock(GoogleOperationJobRepository.class)),
                mock(GoogleCalendarSyncLeaseService.class)
        );

        // when
        service.finalizeOwnedReconciliation(
                9L, 2L, 1L, "run-1", GoogleCalendarSyncMode.FULL,
                Set.of(), Set.of(), Set.of(), "next-token"
        );

        // then
        verify(overrideMappingRepository, never()).deleteAllByIds(any());
        verify(overrideRepository, never()).deleteAllByIds(any());
        verify(overrideMapping, never()).markConflicted();
    }

    @Test
    @DisplayName("unseen recurrence master는 pending child override branch가 있으면 conflict로 보존한다")
    void givenPendingChildOverride_whenMasterIsUnseen_thenQuarantinesMaster() {
        // given
        GoogleCalendarIntegrationRepository integrationRepository =
                mock(GoogleCalendarIntegrationRepository.class);
        GoogleCalendarEventMappingRepository eventMappingRepository =
                mock(GoogleCalendarEventMappingRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository =
                mock(GoogleCalendarRecurrenceEventMappingRepository.class);
        GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository =
                mock(GoogleCalendarRecurrenceOverrideMappingRepository.class);
        GoogleOperationJobRepository jobRepository = mock(GoogleOperationJobRepository.class);
        GoogleOperationJobPersistenceService jobPersistenceService =
                mock(GoogleOperationJobPersistenceService.class);
        GoogleCalendarRecurrenceEventMapping master =
                mock(GoogleCalendarRecurrenceEventMapping.class);
        RecurrenceEvent recurrenceEvent = mock(RecurrenceEvent.class);
        when(master.getId()).thenReturn(10L);
        when(master.getExternalEventId()).thenReturn("master-1");
        when(master.getRecurrenceEvent()).thenReturn(recurrenceEvent);
        when(recurrenceEvent.getId()).thenReturn(100L);
        String recurrenceBaseline =
                GoogleProviderContentProjector.recurrenceMaster(recurrenceEvent);
        when(master.getSyncedContentHash()).thenReturn(recurrenceBaseline);
        when(recurrenceMappingRepository.findNextBatchWithRecurrenceEventByIntegrationIdAfterIdForUpdate(
                eq(1L), eq(0L), any(Pageable.class))).thenReturn(List.of(master));
        when(recurrenceMappingRepository.findNextBatchWithRecurrenceEventByIntegrationIdAfterIdForUpdate(
                eq(1L), eq(10L), any(Pageable.class))).thenReturn(List.of());
        when(overrideMappingRepository
                .findNextBatchWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationIdAfterIdForUpdate(
                        eq(1L), eq(0L), any(Pageable.class))).thenReturn(List.of());
        when(eventMappingRepository.findNextBatchWithEventByIntegrationIdAfterIdForUpdate(
                eq(1L), eq(0L), any(Pageable.class))).thenReturn(List.of());
        when(jobRepository.countPendingRecurrenceAggregateBranches(
                eq(2L), eq(1L), eq("100"), eq("100:"), eq(4)))
                .thenReturn(1L);
        when(integrationRepository.extendSyncLease(1L, "run-1")).thenReturn(1);
        when(integrationRepository.finalizeSync(1L, "run-1", "next-token")).thenReturn(1);
        GoogleCalendarProviderDataService service = new GoogleCalendarProviderDataService(
                integrationRepository, eventMappingRepository, eventRepository,
                recurrenceMappingRepository, overrideMappingRepository,
                mock(RecurrenceEventRepository.class),
                mock(RecurrenceEventOverrideRepository.class), null,
                jobPersistenceService,
                new GoogleCalendarInboundConflictService(jobRepository),
                mock(GoogleCalendarSyncLeaseService.class));

        // when
        service.finalizeOwnedReconciliation(
                9L, 2L, 1L, "run-1", GoogleCalendarSyncMode.FULL,
                Set.of(), Set.of(), Set.of(), "next-token");

        // then
        verify(master).markConflicted();
        verify(jobPersistenceService).markConflictDetected(9L, 2L, "run-1");
        verify(recurrenceMappingRepository, never()).deleteAllByIds(any());
    }

    @Test
    @DisplayName("FULL reconciliation은 잠근 mapping batch를 처리하기 전에 lease를 갱신한다")
    void givenUnseenMappings_whenFinalizeFullSync_thenDeletesByBatchAndRenewsLease() {
        // given
        GoogleCalendarIntegrationRepository integrationRepository =
                mock(GoogleCalendarIntegrationRepository.class);
        GoogleCalendarEventMappingRepository eventMappingRepository =
                mock(GoogleCalendarEventMappingRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        GoogleCalendarRecurrenceEventMappingRepository recurrenceMappingRepository =
                mock(GoogleCalendarRecurrenceEventMappingRepository.class);
        GoogleCalendarRecurrenceOverrideMappingRepository overrideMappingRepository =
                mock(GoogleCalendarRecurrenceOverrideMappingRepository.class);
        RecurrenceEventRepository recurrenceEventRepository =
                mock(RecurrenceEventRepository.class);
        RecurrenceEventOverrideRepository overrideRepository =
                mock(RecurrenceEventOverrideRepository.class);
        GoogleOperationJobPersistenceService operationJobPersistenceService =
                mock(GoogleOperationJobPersistenceService.class);
        GoogleCalendarSyncLeaseService syncLeaseService =
                mock(GoogleCalendarSyncLeaseService.class);
        when(integrationRepository.extendSyncLease(1L, "run-1")).thenReturn(1);
        when(integrationRepository.finalizeSync(1L, "run-1", "next-token")).thenReturn(1);

        GoogleCalendarEventMapping eventMapping = mock(GoogleCalendarEventMapping.class);
        Event event = mock(Event.class);
        when(eventMapping.getId()).thenReturn(10L);
        when(eventMapping.getExternalEventId()).thenReturn("unseen-event");
        when(eventMapping.getEvent()).thenReturn(event);
        when(event.getId()).thenReturn(20L);
        String eventBaseline = GoogleProviderContentProjector.event(event);
        when(eventMapping.getSyncedContentHash()).thenReturn(eventBaseline);
        when(overrideMappingRepository
                .findNextBatchWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationIdAfterIdForUpdate(
                        eq(1L),
                        eq(0L),
                        any(Pageable.class)
                ))
                .thenReturn(List.of());
        when(eventMappingRepository.findNextBatchWithEventByIntegrationIdAfterIdForUpdate(
                eq(1L),
                eq(0L),
                any(Pageable.class)
        )).thenReturn(List.of(eventMapping));
        when(eventMappingRepository.findNextBatchWithEventByIntegrationIdAfterIdForUpdate(
                eq(1L),
                eq(10L),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(recurrenceMappingRepository.findNextBatchWithRecurrenceEventByIntegrationIdAfterIdForUpdate(
                eq(1L),
                eq(0L),
                any(Pageable.class)
        )).thenReturn(List.of());

        GoogleCalendarProviderDataService service = new GoogleCalendarProviderDataService(
                integrationRepository,
                eventMappingRepository,
                eventRepository,
                recurrenceMappingRepository,
                overrideMappingRepository,
                recurrenceEventRepository,
                overrideRepository,
                null,
                operationJobPersistenceService,
                new GoogleCalendarInboundConflictService(
                        mock(GoogleOperationJobRepository.class)),
                syncLeaseService
        );

        // when
        service.finalizeOwnedReconciliation(
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
        verify(eventMappingRepository).deleteAllByIds(List.of(10L));
        verify(eventRepository).deleteAllByIds(List.of(20L));
        verify(eventMappingRepository, times(2)).findNextBatchWithEventByIntegrationIdAfterIdForUpdate(
                eq(1L),
                any(Long.class),
                argThat(page -> page.getPageSize() == 500)
        );
        InOrder mappingFirst = inOrder(eventMappingRepository, syncLeaseService);
        mappingFirst.verify(eventMappingRepository)
                .findNextBatchWithEventByIntegrationIdAfterIdForUpdate(
                        eq(1L), eq(0L), any(Pageable.class)
                );
        mappingFirst.verify(syncLeaseService).renewOwnedLeases(2L, 1L, "run-1");
        verify(syncLeaseService).renewOwnedLeases(2L, 1L, "run-1");
        verify(integrationRepository).extendSyncLease(1L, "run-1");
        verify(integrationRepository).finalizeSync(1L, "run-1", "next-token");
        verify(operationJobPersistenceService)
                .renewAndAssertOwned(9L, 2L, "run-1");
        verify(operationJobPersistenceService).succeed(9L, 2L, "run-1");
    }
}
