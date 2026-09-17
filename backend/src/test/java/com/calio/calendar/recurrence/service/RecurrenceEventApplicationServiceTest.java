package com.calio.calendar.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.domain.GoogleCalendarRecurrenceJobKind;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceJobPayload;
import com.calio.calendar.integration.sync.operation.dto.GoogleRecurrenceOverrideJobPayload;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class RecurrenceEventApplicationServiceTest {

  private final RecurrenceEventService recurrenceEventService = mock();
  private final GoogleOperationJobEnqueueService jobEnqueueService = mock();
  private final RecurrenceEventApplicationService service =
      new RecurrenceEventApplicationService(recurrenceEventService, jobEnqueueService);

  @Test
  @DisplayName("반복 일정 생성 후 생성 Job에 최종 recurrence snapshot을 저장한다")
  void createEnqueuesRecurrenceCreateJob() {
    CreateRecurrenceEventRequest request = createRequest();
    RecurrenceEventResponse response = recurrenceResponse();
    when(recurrenceEventService.createRecurrenceEvent(10L, request)).thenReturn(response);

    service.createRecurrenceEvent(10L, request);

    ArgumentCaptor<GoogleRecurrenceJobPayload> payloadCaptor =
        ArgumentCaptor.forClass(GoogleRecurrenceJobPayload.class);
    InOrder order = inOrder(recurrenceEventService, jobEnqueueService);
    order.verify(recurrenceEventService).createRecurrenceEvent(10L, request);
    order
        .verify(jobEnqueueService)
        .enqueueRecurrence(
            eq(10L),
            eq(40L),
            eq(GoogleCalendarRecurrenceJobKind.RECURRENCE_CREATE),
            payloadCaptor.capture());
    assertThat(payloadCaptor.getValue().title()).isEqualTo("daily");
    assertThat(payloadCaptor.getValue().recurrence()).containsExactly("RRULE:FREQ=DAILY");
  }

  @Test
  @DisplayName("반복 일정 수정 후 수정 Job을 enqueue한다")
  void updateEnqueuesRecurrenceUpdateJob() {
    UpdateRecurrenceEventRequest request = mock();
    RecurrenceEventResponse response = recurrenceResponse();
    when(recurrenceEventService.updateRecurrenceEvent(10L, 40L, request)).thenReturn(response);

    service.updateRecurrenceEvent(10L, 40L, request);

    verify(jobEnqueueService)
        .enqueueRecurrence(
            eq(10L),
            eq(40L),
            eq(GoogleCalendarRecurrenceJobKind.RECURRENCE_UPDATE),
            any(GoogleRecurrenceJobPayload.class));
  }

  @Test
  @DisplayName("반복 일정 삭제 후 master 삭제 Job을 enqueue한다")
  void deleteEnqueuesRecurrenceDeleteJob() {
    service.deleteRecurrenceEvent(10L, 40L);

    verify(recurrenceEventService).deleteRecurrenceEvent(10L, 40L);
    verify(jobEnqueueService).enqueueRecurrenceDeleted(10L, 40L);
  }

  @Test
  @DisplayName("개별 회차 수정 후 exact origin과 최종 snapshot을 enqueue한다")
  void occurrenceUpdateEnqueuesExactOriginJob() {
    Instant originStartAt = Instant.parse("2026-09-04T00:00:00Z");
    UpdateRecurrenceOccurrenceRequest request =
        new UpdateRecurrenceOccurrenceRequest(
            originStartAt,
            "moved",
            null,
            Instant.parse("2026-09-04T02:00:00Z"),
            Instant.parse("2026-09-04T03:00:00Z"),
            false,
            "UTC");
    EventResponse response = mock();
    when(response.title()).thenReturn("moved");
    when(response.startAt()).thenReturn(request.startAt());
    when(response.endAt()).thenReturn(request.endAt());
    when(response.timeZone()).thenReturn("UTC");
    when(recurrenceEventService.updateRecurrenceOccurrence(10L, 40L, request)).thenReturn(response);

    service.updateRecurrenceOccurrence(10L, 40L, request);

    ArgumentCaptor<GoogleRecurrenceOverrideJobPayload> payloadCaptor =
        ArgumentCaptor.forClass(GoogleRecurrenceOverrideJobPayload.class);
    verify(jobEnqueueService)
        .enqueueRecurrenceOverride(eq(10L), eq(40L), eq(originStartAt), payloadCaptor.capture());
    assertThat(payloadCaptor.getValue().title()).isEqualTo("moved");
  }

  @Test
  @DisplayName("개별 회차 삭제 후 exact origin 삭제 Job을 enqueue한다")
  void occurrenceDeleteEnqueuesExactOriginDeleteJob() {
    Instant originStartAt = Instant.parse("2026-09-04T00:00:00Z");

    service.deleteRecurrenceOccurrence(10L, 40L, originStartAt);

    verify(recurrenceEventService).deleteRecurrenceOccurrence(10L, 40L, originStartAt);
    verify(jobEnqueueService).enqueueRecurrenceOverrideDeleted(10L, 40L, originStartAt);
  }

  private CreateRecurrenceEventRequest createRequest() {
    return new CreateRecurrenceEventRequest(
        "daily",
        null,
        false,
        Instant.parse("2026-09-04T00:00:00Z"),
        Instant.parse("2026-09-04T01:00:00Z"),
        "UTC",
        List.of("RRULE:FREQ=DAILY"),
        null);
  }

  private RecurrenceEventResponse recurrenceResponse() {
    return new RecurrenceEventResponse(
        40L,
        "daily",
        null,
        false,
        Instant.parse("2026-09-04T00:00:00Z"),
        Instant.parse("2026-09-04T01:00:00Z"),
        "UTC",
        List.of("RRULE:FREQ=DAILY"),
        null,
        null,
        null,
        true);
  }
}
