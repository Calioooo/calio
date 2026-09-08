package com.calio.calendar.recurrence.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJobKind;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RecurrenceEventApplicationServiceTest {
    private final RecurrenceEventService recurrenceService = mock();
    private final GoogleOperationJobEnqueueService enqueueService = mock();
    private final RecurrenceEventApplicationService service =
            new RecurrenceEventApplicationService(recurrenceService, enqueueService);

    @Test
    @DisplayName("master canonical create 뒤 같은 application transaction 경계에서 narrow Job을 enqueue한다")
    void masterCreateMutatesBeforeEnqueueingSnapshot() {
        CreateRecurrenceEventRequest request = new CreateRecurrenceEventRequest(
                "daily", null, false, Instant.parse("2026-09-04T00:00:00Z"),
                Instant.parse("2026-09-04T01:00:00Z"), "UTC",
                List.of("RRULE:FREQ=DAILY"), null);
        RecurrenceEventResponse response = mock();
        when(response.recurrenceId()).thenReturn(40L);
        when(response.title()).thenReturn("daily");
        when(response.firstOccurrenceStartAt()).thenReturn(request.firstOccurrenceStartAt());
        when(response.firstOccurrenceEndAt()).thenReturn(request.firstOccurrenceEndAt());
        when(response.timeZone()).thenReturn("UTC");
        when(response.recurrence()).thenReturn(request.recurrence());
        when(recurrenceService.createRecurrenceEvent(10L, request)).thenReturn(response);

        service.createRecurrenceEvent(10L, request);

        InOrder order = inOrder(recurrenceService, enqueueService);
        order.verify(recurrenceService).createRecurrenceEvent(10L, request);
        order.verify(enqueueService).enqueueRecurrenceMaster(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(40L),
                org.mockito.ArgumentMatchers.eq(GoogleCalendarRecurrenceJobKind.MASTER_CREATE), any());
    }

    @Test
    @DisplayName("override mutation은 exact origin과 해당 full final snapshot만 enqueue한다")
    void overrideMutationEnqueuesExactOriginSnapshot() {
        Instant origin = Instant.parse("2026-09-04T00:00:00Z");
        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest(
                origin, "moved", null, Instant.parse("2026-09-04T02:00:00Z"),
                Instant.parse("2026-09-04T03:00:00Z"), false, "UTC");
        EventResponse response = mock();
        when(response.title()).thenReturn("moved");
        when(response.startAt()).thenReturn(request.startAt());
        when(response.endAt()).thenReturn(request.endAt());
        when(response.timeZone()).thenReturn("UTC");
        when(recurrenceService.updateRecurrenceOccurrence(10L, 40L, request)).thenReturn(response);

        service.updateRecurrenceOccurrence(10L, 40L, request);

        verify(enqueueService).enqueueRecurrenceOverride(
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq(40L),
                org.mockito.ArgumentMatchers.eq(origin), any());
        verify(enqueueService, never()).enqueueRecurrenceMaster(any(), any(), any(), any());
    }
}
