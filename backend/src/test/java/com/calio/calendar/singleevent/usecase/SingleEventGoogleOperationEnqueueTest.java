package com.calio.calendar.singleevent.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService;
import com.calio.calendar.integration.sync.operation.GoogleOperationJobEnqueueService.OutboundOperation;
import com.calio.calendar.sharing.event.service.PersonalEventGroupShareCommandService;
import com.calio.calendar.singleevent.controller.dto.CreateSingleEventRequest;
import com.calio.calendar.singleevent.controller.dto.UpdateSingleEventRequest;
import com.calio.calendar.singleevent.domain.SingleEvent;
import com.calio.calendar.singleevent.repository.SingleEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class SingleEventGoogleOperationEnqueueTest {

  private static final long ACCOUNT_ID = 1L;
  private static final long EVENT_ID = 10L;

  private final AccountRepository accountRepository =
      org.mockito.Mockito.mock(AccountRepository.class);
  private final SingleEventRepository eventRepository =
      org.mockito.Mockito.mock(SingleEventRepository.class);
  private final TagRepository tagRepository = org.mockito.Mockito.mock(TagRepository.class);
  private final PersonalEventGroupShareCommandService shareCommandService =
      org.mockito.Mockito.mock(PersonalEventGroupShareCommandService.class);
  private final GoogleOperationJobEnqueueService jobEnqueueService =
      org.mockito.Mockito.mock(GoogleOperationJobEnqueueService.class);

  @Test
  @DisplayName("SingleEvent 생성 후 생성 job을 enqueue한다")
  void givenCreatedSingleEvent_whenCreate_thenEnqueuesCreateJob() {
    Tag tag = tag();
    OutboundOperation outboundOperation = mock();
    when(jobEnqueueService.prepareOutboundOperation(ACCOUNT_ID)).thenReturn(outboundOperation);
    when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(new Account()));
    when(tagRepository.findPersonalFallbackTag()).thenReturn(Optional.of(tag));
    when(eventRepository.save(any(SingleEvent.class)))
        .thenAnswer(
            invocation -> {
              SingleEvent event = invocation.getArgument(0);
              ReflectionTestUtils.setField(event, "id", EVENT_ID);
              return event;
            });

    new CreateSingleEventUseCase(
            accountRepository, eventRepository, tagRepository, jobEnqueueService)
        .create(ACCOUNT_ID, request());

    InOrder ordered = inOrder(jobEnqueueService, eventRepository);
    ordered.verify(jobEnqueueService).prepareOutboundOperation(ACCOUNT_ID);
    ordered.verify(eventRepository).save(any(SingleEvent.class));
    ordered.verify(jobEnqueueService).enqueueEventCreated(eq(outboundOperation), any());
  }

  @Test
  @DisplayName("SingleEvent 수정 후 flush한 snapshot으로 수정 job을 enqueue한다")
  void givenUpdatedSingleEvent_whenUpdate_thenFlushesAndEnqueuesUpdateJob() {
    SingleEvent event = eventWithId();
    Tag tag = tag();
    OutboundOperation outboundOperation = mock();
    when(jobEnqueueService.prepareOutboundOperation(ACCOUNT_ID)).thenReturn(outboundOperation);
    when(eventRepository.findByIdAndAccountIdForUpdate(EVENT_ID, ACCOUNT_ID))
        .thenReturn(Optional.of(event));
    when(tagRepository.findPersonalFallbackTag()).thenReturn(Optional.of(tag));

    new UpdateSingleEventUseCase(eventRepository, tagRepository, jobEnqueueService)
        .update(ACCOUNT_ID, EVENT_ID, updateRequest());

    InOrder ordered = inOrder(eventRepository, jobEnqueueService);
    ordered.verify(jobEnqueueService).prepareOutboundOperation(ACCOUNT_ID);
    ordered.verify(eventRepository).findByIdAndAccountIdForUpdate(EVENT_ID, ACCOUNT_ID);
    ordered.verify(eventRepository).flush();
    ordered.verify(jobEnqueueService).enqueueEventUpdated(outboundOperation, event);
  }

  @Test
  @DisplayName("SingleEvent 삭제 후 삭제 job을 enqueue한다")
  void givenDeletedSingleEvent_whenDelete_thenEnqueuesDeleteJobAfterLocalDeletion() {
    SingleEvent event = eventWithId();
    OutboundOperation outboundOperation = mock();
    when(jobEnqueueService.prepareOutboundOperation(ACCOUNT_ID)).thenReturn(outboundOperation);
    when(eventRepository.findByIdAndAccountIdForUpdate(EVENT_ID, ACCOUNT_ID))
        .thenReturn(Optional.of(event));

    new DeleteSingleEventUseCase(eventRepository, shareCommandService, jobEnqueueService)
        .delete(ACCOUNT_ID, EVENT_ID);

    InOrder ordered = inOrder(shareCommandService, eventRepository, jobEnqueueService);
    ordered.verify(jobEnqueueService).prepareOutboundOperation(ACCOUNT_ID);
    ordered.verify(eventRepository).findByIdAndAccountIdForUpdate(EVENT_ID, ACCOUNT_ID);
    ordered.verify(shareCommandService).deleteAllForSourceEvent(EVENT_ID);
    ordered.verify(eventRepository).delete(event);
    ordered.verify(jobEnqueueService).enqueueEventDeleted(outboundOperation, EVENT_ID);
  }

  private CreateSingleEventRequest request() {
    return new CreateSingleEventRequest(
        "Created",
        null,
        Instant.parse("2027-01-01T00:00:00Z"),
        Instant.parse("2027-01-01T01:00:00Z"),
        false,
        "UTC",
        null);
  }

  private UpdateSingleEventRequest updateRequest() {
    return new UpdateSingleEventRequest(
        "Updated",
        null,
        Instant.parse("2027-01-02T00:00:00Z"),
        Instant.parse("2027-01-02T01:00:00Z"),
        false,
        "UTC",
        null);
  }

  private SingleEvent eventWithId() {
    SingleEvent event =
        new SingleEvent(
            "Original",
            null,
            Instant.parse("2027-01-01T00:00:00Z"),
            Instant.parse("2027-01-01T01:00:00Z"),
            false,
            "UTC",
            2L,
            ACCOUNT_ID);
    ReflectionTestUtils.setField(event, "id", EVENT_ID);
    return event;
  }

  private Tag tag() {
    Tag tag = Tag.personalFallback("기타", "#64748B");
    ReflectionTestUtils.setField(tag, "id", 2L);
    return tag;
  }
}
