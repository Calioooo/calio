package com.calio.calendar.integration.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.integration.domain.GoogleCalendarEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarSyncMode;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarIntegrationRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.integration.repository.GoogleOperationJobRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class GoogleCalendarProviderDataServiceTest {

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
        when(master.getId()).thenReturn(10L);
        when(master.getExternalEventId()).thenReturn("master-1");
        when(recurrenceMappingRepository.findNextBatchWithRecurrenceEventByIntegrationId(
                eq(1L), eq(0L), any(Pageable.class))).thenReturn(List.of(master));
        when(recurrenceMappingRepository.findNextBatchWithRecurrenceEventByIntegrationId(
                eq(1L), eq(10L), any(Pageable.class))).thenReturn(List.of());
        when(overrideMappingRepository
                .findNextBatchWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationId(
                        eq(1L), eq(0L), any(Pageable.class))).thenReturn(List.of());
        when(eventMappingRepository.findNextBatchWithEventByIntegrationId(
                eq(1L), eq(0L), any(Pageable.class))).thenReturn(List.of());
        when(jobRepository.countPendingRecurrenceAggregateBranches(
                eq(2L), eq(1L), eq("master-1"), eq("8:master-1:"), eq(11)))
                .thenReturn(1L);
        when(integrationRepository.extendSyncLease(1L, "run-1")).thenReturn(1);
        when(integrationRepository.finalizeSync(1L, "run-1", "next-token")).thenReturn(1);
        GoogleCalendarProviderDataService service = new GoogleCalendarProviderDataService(
                integrationRepository, eventMappingRepository, eventRepository,
                recurrenceMappingRepository, overrideMappingRepository,
                mock(RecurrenceEventRepository.class),
                mock(RecurrenceEventOverrideRepository.class), null,
                jobPersistenceService, jobRepository);

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
    @DisplayName("FULL reconciliation은 각 mapping batch에서 sync lease와 operation ownership을 갱신한다")
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
        when(integrationRepository.extendSyncLease(1L, "run-1")).thenReturn(1);
        when(integrationRepository.finalizeSync(1L, "run-1", "next-token")).thenReturn(1);

        GoogleCalendarEventMapping eventMapping = mock(GoogleCalendarEventMapping.class);
        Event event = mock(Event.class);
        when(eventMapping.getId()).thenReturn(10L);
        when(eventMapping.getExternalEventId()).thenReturn("unseen-event");
        when(eventMapping.getEvent()).thenReturn(event);
        when(event.getId()).thenReturn(20L);
        when(overrideMappingRepository
                .findNextBatchWithRecurrenceEventMappingAndRecurrenceEventOverrideByIntegrationId(
                        eq(1L),
                        eq(0L),
                        any(Pageable.class)
                ))
                .thenReturn(List.of());
        when(eventMappingRepository.findNextBatchWithEventByIntegrationId(
                eq(1L),
                eq(0L),
                any(Pageable.class)
        )).thenReturn(List.of(eventMapping));
        when(eventMappingRepository.findNextBatchWithEventByIntegrationId(
                eq(1L),
                eq(10L),
                any(Pageable.class)
        )).thenReturn(List.of());
        when(recurrenceMappingRepository.findNextBatchWithRecurrenceEventByIntegrationId(
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
                operationJobPersistenceService
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
        verify(eventMappingRepository, times(2)).findNextBatchWithEventByIntegrationId(
                eq(1L),
                any(Long.class),
                argThat(page -> page.getPageSize() == 500)
        );
        verify(integrationRepository, times(5)).extendSyncLease(1L, "run-1");
        verify(integrationRepository).finalizeSync(1L, "run-1", "next-token");
        verify(operationJobPersistenceService, times(6))
                .renewAndAssertOwned(9L, 2L, "run-1");
        verify(operationJobPersistenceService).succeed(9L, 2L, "run-1");
    }
}
