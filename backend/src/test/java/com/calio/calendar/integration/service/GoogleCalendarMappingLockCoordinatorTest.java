package com.calio.calendar.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.domain.GoogleCalendarEffectiveScope;
import com.calio.calendar.integration.domain.GoogleCalendarIntegration;
import com.calio.calendar.integration.domain.GoogleCalendarMappingSyncStatus;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceEventMapping;
import com.calio.calendar.integration.domain.GoogleCalendarRecurrenceOverrideMapping;
import com.calio.calendar.integration.repository.GoogleCalendarEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceEventMappingRepository;
import com.calio.calendar.integration.repository.GoogleCalendarRecurrenceOverrideMappingRepository;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.EventCancellation;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventCancellation;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.RecurrenceEventOverrideUpsert;
import com.calio.calendar.integration.service.GoogleCalendarNormalizedPage.CancelledRecurrenceEventOverrideUpsert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GoogleCalendarMappingLockCoordinatorTest {

    private final GoogleCalendarEventMappingRepository eventMappings =
            mock(GoogleCalendarEventMappingRepository.class);
    private final GoogleCalendarRecurrenceEventMappingRepository recurrenceEventMappings =
            mock(GoogleCalendarRecurrenceEventMappingRepository.class);
    private final GoogleCalendarRecurrenceOverrideMappingRepository overrideMappings =
            mock(GoogleCalendarRecurrenceOverrideMappingRepository.class);
    private final GoogleCalendarMappingLockCoordinator coordinator =
            new GoogleCalendarMappingLockCoordinator(
                    eventMappings, recurrenceEventMappings, overrideMappings
            );

    @Test
    @DisplayName("페이지 item 순서와 무관하게 event, recurrence-event, override 순서로 잠근다")
    void givenMixedProviderOrder_whenLockingPage_thenUsesFixedMappingOrder() {
        RecurrenceEventOverrideUpsert override = new CancelledRecurrenceEventOverrideUpsert(
                "override-1",
                "recurrence-event-1",
                Instant.parse("2026-08-05T00:00:00Z"),
                "etag",
                Instant.parse("2026-08-05T01:00:00Z")
        );
        when(eventMappings.findAllWithEventByExternalIdentityForUpdate(
                eq(1L), eq("primary"), anyCollection())).thenReturn(List.of());
        when(recurrenceEventMappings
                .findAllWithRecurrenceEventAndTagByExternalIdentityForUpdate(
                        eq(1L), eq("primary"), anyCollection())).thenReturn(List.of());
        when(overrideMappings
                .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByExternalEventIdsForUpdate(
                        eq(1L), eq("primary"), anyCollection())).thenReturn(List.of());

        coordinator.lockPage(1L, List.of(
                override,
                new EventCancellation("event-1"),
                new RecurrenceEventCancellation("recurrence-event-1")
        ));

        InOrder lockOrder = inOrder(eventMappings, recurrenceEventMappings, overrideMappings);
        lockOrder.verify(eventMappings).findAllWithEventByExternalIdentityForUpdate(
                eq(1L), eq("primary"), anyCollection());
        lockOrder.verify(recurrenceEventMappings)
                .findAllWithRecurrenceEventAndTagByExternalIdentityForUpdate(
                        eq(1L), eq("primary"), anyCollection());
        lockOrder.verify(overrideMappings)
                .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByExternalEventIdsForUpdate(
                        eq(1L), eq("primary"), anyCollection());
    }

    @Test
    @DisplayName("conflicted recurrence-event는 child override 조회 전에 전체 scope를 차단한다")
    void givenConflictedRecurrenceEvent_whenCheckingOverride_thenSkipsOverrideLock() {
        GoogleCalendarRecurrenceEventMapping recurrenceEvent =
                mock(GoogleCalendarRecurrenceEventMapping.class);
        when(recurrenceEvent.getSyncStatus())
                .thenReturn(GoogleCalendarMappingSyncStatus.CONFLICTED);
        when(recurrenceEventMappings
                .findWithRecurrenceEventByIntegrationIdAndRecurrenceEventIdForUpdate(1L, 10L))
                .thenReturn(Optional.of(recurrenceEvent));

        boolean conflicted = coordinator.isConflictedAfterLock(
                1L,
                GoogleCalendarEffectiveScope.recurrenceOverride(
                        10L, Instant.parse("2026-08-05T00:00:00Z"))
        );

        assertThat(conflicted).isTrue();
        verify(overrideMappings, never())
                .findWithRecurrenceEventMappingAndRecurrenceEventOverrideByScopeForUpdate(
                        eq(1L), eq(10L), eq(Instant.parse("2026-08-05T00:00:00Z"))
                );
    }

    @Test
    @DisplayName("다른 recurrence-event에 연결된 override external id는 잠근 상태에서 거부한다")
    void givenOverrideExternalIdOwnedByOtherRecurrenceEvent_whenLockingPage_thenRejectsIt() {
        RecurrenceEventOverrideUpsert override = new CancelledRecurrenceEventOverrideUpsert(
                "override-1",
                "requested-recurrence-event",
                Instant.parse("2026-08-05T00:00:00Z"),
                "etag",
                Instant.parse("2026-08-05T01:00:00Z")
        );
        GoogleCalendarIntegration integration = mock(GoogleCalendarIntegration.class);
        GoogleCalendarRecurrenceEventMapping existingRecurrenceEvent =
                mock(GoogleCalendarRecurrenceEventMapping.class);
        GoogleCalendarRecurrenceOverrideMapping existingOverride =
                mock(GoogleCalendarRecurrenceOverrideMapping.class);
        when(integration.getId()).thenReturn(1L);
        when(existingRecurrenceEvent.getId()).thenReturn(10L);
        when(existingRecurrenceEvent.getIntegration()).thenReturn(integration);
        when(existingRecurrenceEvent.getExternalEventId())
                .thenReturn("other-recurrence-event");
        when(existingOverride.getRecurrenceEventMapping()).thenReturn(existingRecurrenceEvent);
        when(existingOverride.getExternalEventId()).thenReturn("override-1");
        when(eventMappings.findAllWithEventByExternalIdentityForUpdate(
                eq(1L), eq("primary"), anyCollection())).thenReturn(List.of());
        when(recurrenceEventMappings
                .findAllWithRecurrenceEventAndTagByExternalIdentityForUpdate(
                        eq(1L), eq("primary"), anyCollection())).thenReturn(List.of());
        when(overrideMappings
                .findAllWithRecurrenceEventMappingAndRecurrenceEventOverrideByExternalEventIdsForUpdate(
                        eq(1L), eq("primary"), anyCollection()))
                .thenReturn(List.of(existingOverride));

        assertThatThrownBy(() -> coordinator.lockPage(1L, List.of(override)))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_EVENT_RESPONSE_INVALID));
    }
}
