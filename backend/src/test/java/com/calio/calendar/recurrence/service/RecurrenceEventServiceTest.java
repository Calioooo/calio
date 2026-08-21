package com.calio.calendar.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.service.EventCommandService;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.RecurrenceEventResponse;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.service.TagQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecurrenceEventServiceTest {

    @Mock
    private RecurrenceEventRepository recurrenceEventRepository;

    @Mock
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @Mock
    private AccountQueryService accountQueryService;

    @Mock
    private TagQueryService tagQueryService;

    @Mock
    private EventCommandService eventCommandService;

    @Mock
    private Rfc5545RecurrenceEngine recurrenceEngine;

    @Mock
    private Clock clock;

    private RecurrenceEventService recurrenceEventService;

    @BeforeEach
    void setUp() {
        RecurrenceEventQueryService queryService = new RecurrenceEventQueryService(
                recurrenceEventRepository,
                recurrenceEventOverrideRepository
        );
        RecurrenceEventCommandService commandService = new RecurrenceEventCommandService(
                recurrenceEventRepository,
                recurrenceEventOverrideRepository
        );
        recurrenceEventService = new RecurrenceEventService(
                queryService,
                commandService,
                accountQueryService,
                tagQueryService,
                eventCommandService,
                recurrenceEngine,
                clock
        );
    }

    @Test
    @DisplayName("반복 일정 생성은 정규화된 RFC line과 canonical schedule만 저장하고 Event row를 만들지 않는다")
    void givenTimedRequest_whenCreate_thenStoresValidatedMasterWithoutMaterializingEvents() {
        // given
        Tag tag = tag();
        List<String> normalized = List.of("RRULE:FREQ=DAILY;COUNT=3");
        when(tagQueryService.getTagOrDefault(1L, null)).thenReturn(tag);
        when(recurrenceEngine.validate(any(RecurrenceSchedule.class), any())).thenReturn(normalized);
        when(recurrenceEventRepository.save(any(RecurrenceEvent.class))).thenAnswer(invocation -> {
            RecurrenceEvent recurrenceEvent = invocation.getArgument(0);
            ReflectionTestUtils.setField(recurrenceEvent, "id", 10L);
            return recurrenceEvent;
        });
        CreateRecurrenceEventRequest request = timedCreateRequest();

        // when
        recurrenceEventService.createRecurrenceEvent(1L, request);

        // then
        ArgumentCaptor<RecurrenceEvent> captor = ArgumentCaptor.forClass(RecurrenceEvent.class);
        verify(recurrenceEventRepository).save(captor.capture());
        assertThat(captor.getValue().getFirstOccurrenceStartAt())
                .isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
        assertThat(captor.getValue().getFirstOccurrenceEndAt())
                .isEqualTo(Instant.parse("2027-01-01T01:00:00Z"));
        assertThat(captor.getValue().getTimeZone()).isEqualTo("Asia/Seoul");
        assertThat(captor.getValue().getRecurrenceRules()).containsExactlyElementsOf(normalized);
        verifyNoInteractions(eventCommandService);
    }

    @Test
    @DisplayName("반복 일정 조회는 QueryService의 domain entity를 응답 DTO로 변환한다")
    void givenOwnedRecurrenceEvent_whenGet_thenCreatesResponse() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        when(recurrenceEventRepository.findByIdAndAccount_Id(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));

        // when
        RecurrenceEventResponse response = recurrenceEventService.getRecurrenceEvent(1L, 10L);

        // then
        assertThat(response.recurrenceId()).isEqualTo(10L);
        assertThat(response.title()).isEqualTo("Rule");
        assertThat(response.description()).isEqualTo("memo");
        assertThat(response.recurrence()).containsExactly("RRULE:FREQ=DAILY;COUNT=3");
    }

    @Test
    @DisplayName("전체 수정은 새 정의 검증 후 master snapshot만 교체하고 기존 child 상태를 보존한다")
    void givenValidUpdate_whenUpdate_thenReplacesOnlyMaster() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Tag tag = tag();
        List<String> normalized = List.of("RRULE:FREQ=WEEKLY;COUNT=2");
        when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));
        when(tagQueryService.getTagOrDefault(1L, null)).thenReturn(tag);
        when(recurrenceEngine.validate(any(RecurrenceSchedule.class), any())).thenReturn(normalized);
        UpdateRecurrenceEventRequest request = new UpdateRecurrenceEventRequest(
                "Updated",
                null,
                true,
                Instant.parse("2027-02-01T00:00:00Z"),
                Instant.parse("2027-02-03T00:00:00Z"),
                null,
                normalized,
                null
        );

        // when
        recurrenceEventService.updateRecurrenceEvent(1L, 10L, request);

        // then
        assertThat(recurrenceEvent.getTitle()).isEqualTo("Updated");
        assertThat(recurrenceEvent.isAllDay()).isTrue();
        assertThat(recurrenceEvent.getTimeZone()).isNull();
        verify(recurrenceEventOverrideRepository, never()).deleteAllByRecurrenceEventIds(any());
        verify(eventCommandService, never()).deleteEventsByRecurrenceEventIds(any());
    }

    @Test
    @DisplayName("전체 recurrence 삭제는 override와 account legacy Event를 master보다 먼저 제거한다")
    void givenRecurrenceChildren_whenDeleteMaster_thenDeletesChildrenBeforeMaster() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));

        // when
        recurrenceEventService.deleteRecurrenceEvent(1L, 10L);

        // then
        InOrder deletionOrder = inOrder(
                recurrenceEventRepository,
                recurrenceEventOverrideRepository,
                eventCommandService
        );
        deletionOrder.verify(recurrenceEventRepository).findByIdAndAccountIdForUpdate(10L, 1L);
        deletionOrder.verify(recurrenceEventOverrideRepository).deleteAllByRecurrenceEventIds(List.of(10L));
        deletionOrder.verify(eventCommandService).deleteEventsByRecurrenceEventIds(List.of(10L));
        deletionOrder.verify(recurrenceEventRepository).deleteAllByIds(List.of(10L));
    }

    @Test
    @DisplayName("occurrence PATCH는 title과 null description을 포함한 완전한 snapshot을 저장한다")
    void givenOccurrencePatch_whenUpdate_thenStoresCompleteSnapshot() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Instant originStartAt = Instant.parse("2027-01-01T00:00:00Z");
        when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));
        when(recurrenceEngine.containsOrigin(any(), any(), any())).thenReturn(true);
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt))
                .thenReturn(Optional.empty());
        when(recurrenceEventOverrideRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest(
                originStartAt,
                "Final title",
                null,
                Instant.parse("2027-01-03T02:00:00Z"),
                Instant.parse("2027-01-03T03:00:00Z"),
                false,
                "Asia/Seoul"
        );

        // when
        EventResponse response = recurrenceEventService.updateRecurrenceOccurrence(1L, 10L, request);

        // then
        ArgumentCaptor<RecurrenceEventOverride> captor = ArgumentCaptor.forClass(RecurrenceEventOverride.class);
        verify(recurrenceEventOverrideRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOverrideTitle()).isEqualTo("Final title");
        assertThat(captor.getValue().getOverrideDescription()).isNull();
        assertThat(captor.getValue().getOverrideTimeZone()).isEqualTo("Asia/Seoul");
        assertThat(response.title()).isEqualTo("Final title");
        assertThat(response.description()).isNull();
        assertThat(response.originStartAt()).isEqualTo(originStartAt);
    }

    @Test
    @DisplayName("기존 override와 현재 recurrence 회차가 모두 없으면 PATCH 상태를 생성하지 않는다")
    void givenUnknownOriginWithoutOverride_whenUpdate_thenRejectsWithoutStateChange() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Instant originStartAt = Instant.parse("2027-01-01T00:00:01Z");
        when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt))
                .thenReturn(Optional.empty());
        when(recurrenceEngine.containsOrigin(any(), any(), any())).thenReturn(false);
        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest(
                originStartAt,
                "Unknown occurrence",
                null,
                Instant.parse("2027-01-03T02:00:00Z"),
                Instant.parse("2027-01-03T03:00:00Z"),
                false,
                "Asia/Seoul"
        );

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.updateRecurrenceOccurrence(1L, 10L, request))
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
        verify(recurrenceEventOverrideRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("현재 rule에서 사라진 deleted override PATCH는 같은 identity를 현재 master 형식으로 복원한다")
    void givenDeletedOrphanOverride_whenPatch_thenRestoresExactRowWithCurrentMasterScheduleType() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Instant originStartAt = Instant.parse("2027-01-01T00:00:00Z");
        RecurrenceEventOverride existingOverride = RecurrenceEventOverride.active(
                recurrenceEvent,
                originStartAt,
                "Old title",
                "old memo",
                CanonicalSchedule.recurrenceOverride(
                        Instant.parse("2027-01-02T02:00:00Z"),
                        Instant.parse("2027-01-02T03:00:00Z"),
                        false,
                        "Asia/Seoul"
                )
        );
        existingOverride.markDeleted(Instant.parse("2027-01-05T00:00:00Z"));
        recurrenceEvent.update(
                "All day master",
                null,
                RecurrenceSchedule.create(
                        true,
                        Instant.parse("2027-02-01T00:00:00Z"),
                        Instant.parse("2027-02-02T00:00:00Z"),
                        null
                ),
                List.of("RRULE:FREQ=WEEKLY;COUNT=2"),
                tag()
        );
        when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt))
                .thenReturn(Optional.of(existingOverride));
        when(recurrenceEventOverrideRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest(
                originStartAt,
                "Restored",
                null,
                Instant.parse("2027-03-01T00:00:00Z"),
                Instant.parse("2027-03-03T00:00:00Z"),
                true,
                null
        );

        // when
        recurrenceEventService.updateRecurrenceOccurrence(1L, 10L, request);

        // then
        verify(recurrenceEngine, never()).containsOrigin(any(), any(), any());
        verify(recurrenceEventOverrideRepository).saveAndFlush(existingOverride);
        assertThat(existingOverride.getOriginStartAt()).isEqualTo(originStartAt);
        assertThat(existingOverride.getOverrideTitle()).isEqualTo("Restored");
        assertThat(existingOverride.getOverrideStartAt()).isEqualTo(request.startAt());
        assertThat(existingOverride.getOverrideEndAt()).isEqualTo(request.endAt());
        assertThat(existingOverride.isOverrideAllDay()).isTrue();
        assertThat(existingOverride.getOverrideTimeZone()).isNull();
        assertThat(existingOverride.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("현재 rule에서 사라진 active override DELETE는 같은 identity를 삭제 상태로 전환한다")
    void givenActiveOrphanOverride_whenDelete_thenMarksExactRowDeleted() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Instant originStartAt = Instant.parse("2027-01-01T00:00:00Z");
        Instant deletedAt = Instant.parse("2027-01-06T00:00:00Z");
        RecurrenceEventOverride existingOverride = RecurrenceEventOverride.active(
                recurrenceEvent,
                originStartAt,
                "Override",
                null,
                CanonicalSchedule.recurrenceOverride(
                        Instant.parse("2027-01-02T02:00:00Z"),
                        Instant.parse("2027-01-02T03:00:00Z"),
                        false,
                        "Asia/Seoul"
                )
        );
        when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt))
                .thenReturn(Optional.of(existingOverride));
        when(clock.instant()).thenReturn(deletedAt);

        // when
        recurrenceEventService.deleteRecurrenceOccurrence(1L, 10L, originStartAt);

        // then
        verify(recurrenceEngine, never()).containsOrigin(any(), any(), any());
        verify(recurrenceEventOverrideRepository).saveAndFlush(existingOverride);
        assertThat(existingOverride.getOriginStartAt()).isEqualTo(originStartAt);
        assertThat(existingOverride.isDeleted()).isTrue();
        assertThat(existingOverride.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    @DisplayName("현재 rule과 exact override에 없는 origin은 상태를 만들지 않고 거절한다")
    void givenUnknownOriginWithoutOverride_whenDelete_thenRejectsWithoutStateChange() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Instant originStartAt = Instant.parse("2027-01-01T00:00:01Z");
        when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt))
                .thenReturn(Optional.empty());
        when(recurrenceEngine.containsOrigin(any(), any(), any())).thenReturn(false);

        // when, then
        assertThatThrownBy(() ->
                recurrenceEventService.deleteRecurrenceOccurrence(1L, 10L, originStartAt)
        )
                .isInstanceOf(CalioException.class)
                .extracting(exception -> ((CalioException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
        verify(recurrenceEventOverrideRepository, never()).saveAndFlush(any());
    }

    private CreateRecurrenceEventRequest timedCreateRequest() {
        return new CreateRecurrenceEventRequest(
                "Rule",
                "memo",
                false,
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T01:00:00Z"),
                "Asia/Seoul",
                List.of("RRULE:FREQ=DAILY;COUNT=3"),
                null
        );
    }

    private RecurrenceEvent recurrenceEvent() {
        RecurrenceEvent recurrenceEvent = new RecurrenceEvent(
                "Rule",
                "memo",
                RecurrenceSchedule.create(
                        false,
                        Instant.parse("2027-01-01T00:00:00Z"),
                        Instant.parse("2027-01-01T01:00:00Z"),
                        "Asia/Seoul"
                ),
                List.of("RRULE:FREQ=DAILY;COUNT=3"),
                tag(),
                account()
        );
        ReflectionTestUtils.setField(recurrenceEvent, "id", 10L);
        return recurrenceEvent;
    }

    private Tag tag() {
        return new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B");
    }

    private Account account() {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", 1L);
        return account;
    }
}
