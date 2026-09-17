package com.calio.calendar.recurrence.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventChangePublisher;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.recurrence.service.Rfc5545RecurrenceEngine;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class RecurrenceEventUseCaseTest {

  private final AccountRepository accountRepository = mock();
  private final TagRepository tagRepository = mock();
  private final RecurrenceEventRepository recurrenceEventRepository = mock();
  private final RecurrenceEventOverrideRepository overrideRepository = mock();
  private final Rfc5545RecurrenceEngine recurrenceEngine = mock();
  private final RecurrenceEventChangePublisher changePublisher = mock();

  @Test
  @DisplayName("반복 일정 생성 UseCase는 저장된 최종 상태를 같은 트랜잭션에서 발행한다")
  void createPersistsBeforePublishingFinalState() {
    CreateRecurrenceEventRequest request =
        new CreateRecurrenceEventRequest(
            "daily",
            "description",
            false,
            Instant.parse("2026-09-04T00:00:00Z"),
            Instant.parse("2026-09-04T01:00:00Z"),
            "UTC",
            List.of("RRULE:FREQ=DAILY"),
            null);
    Account account = new Account();
    Tag fallbackTag = Tag.personalDefault("기타", "#64748B");
    ReflectionTestUtils.setField(fallbackTag, "id", 3L);
    when(accountRepository.findById(10L)).thenReturn(Optional.of(account));
    when(tagRepository.findFirstByTagTypeAndTitleAndAccountIsNullAndGroupSpaceIsNullOrderByIdAsc(
            TagType.PERSONAL_DEFAULT, "기타"))
        .thenReturn(Optional.of(fallbackTag));
    when(recurrenceEngine.validate(any(), any())).thenReturn(request.recurrence());
    when(recurrenceEventRepository.saveAndFlush(any()))
        .thenAnswer(
            invocation -> {
              RecurrenceEvent recurrenceEvent = invocation.getArgument(0);
              ReflectionTestUtils.setField(recurrenceEvent, "id", 40L);
              return recurrenceEvent;
            });
    CreateRecurrenceEventUseCase useCase =
        new CreateRecurrenceEventUseCase(
            accountRepository,
            tagRepository,
            recurrenceEventRepository,
            recurrenceEngine,
            changePublisher);

    RecurrenceEventResponse response = useCase.create(10L, request);

    InOrder order = inOrder(recurrenceEventRepository, changePublisher);
    order.verify(recurrenceEventRepository).saveAndFlush(any(RecurrenceEvent.class));
    order.verify(changePublisher).recurrenceEventCreated(eq(10L), any(RecurrenceEvent.class));
    assertThat(response.recurrenceId()).isEqualTo(40L);
    assertThat(response.title()).isEqualTo("daily");
  }

  @Test
  @DisplayName("개별 회차 수정 UseCase는 정확한 origin의 최종 override를 저장하고 발행한다")
  void updateOccurrencePersistsAndPublishesExactOrigin() {
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
    RecurrenceEvent recurrenceEvent = recurrenceEvent();
    when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(40L, 10L))
        .thenReturn(Optional.of(recurrenceEvent));
    when(overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(40L, originStartAt))
        .thenReturn(Optional.empty());
    when(recurrenceEngine.containsOrigin(any(), any(), any())).thenReturn(true);
    when(overrideRepository.saveAndFlush(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    UpdateRecurrenceOccurrenceUseCase useCase =
        new UpdateRecurrenceOccurrenceUseCase(
            recurrenceEventRepository, overrideRepository, recurrenceEngine, changePublisher);

    EventResponse response = useCase.update(10L, 40L, request);

    verify(changePublisher)
        .recurrenceOccurrenceUpdated(
            eq(10L),
            org.mockito.ArgumentMatchers.argThat(
                override ->
                    override.getOriginStartAt().equals(originStartAt)
                        && override.getOverrideTitle().equals("moved")));
    assertThat(response.originStartAt()).isEqualTo(originStartAt);
    assertThat(response.title()).isEqualTo("moved");
  }

  private RecurrenceEvent recurrenceEvent() {
    RecurrenceEvent recurrenceEvent =
        new RecurrenceEvent(
            "daily",
            null,
            com.calio.calendar.recurrence.domain.RecurrenceSchedule.create(
                false,
                Instant.parse("2026-09-04T00:00:00Z"),
                Instant.parse("2026-09-04T01:00:00Z"),
                "UTC"),
            List.of("RRULE:FREQ=DAILY"),
            mock(Tag.class),
            mock(Account.class));
    ReflectionTestUtils.setField(recurrenceEvent, "id", 40L);
    return recurrenceEvent;
  }
}
