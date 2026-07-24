package com.calio.calendar.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.controller.dto.EventResponse;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.recurrence.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.recurrence.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.service.TagService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecurrenceEventServiceTest {

    @Mock
    private RecurrenceEventRepository recurrenceEventRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TagService tagService;

    @Mock
    private Rfc5545RecurrenceEngine recurrenceEngine;

    @InjectMocks
    private RecurrenceEventService recurrenceEventService;

    @Test
    @DisplayName("반복 일정 생성은 정규화된 RFC line과 canonical schedule만 저장하고 Event row를 만들지 않는다")
    void givenTimedRequest_whenCreate_thenStoresValidatedMasterWithoutMaterializingEvents() {
        // given
        Tag tag = tag();
        List<String> normalized = List.of("RRULE:FREQ=DAILY;COUNT=3");
        when(tagService.getTagOrDefault(1L, null)).thenReturn(tag);
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
        assertThat(captor.getValue().getStartAt()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
        assertThat(captor.getValue().getEndAt()).isEqualTo(Instant.parse("2027-01-01T01:00:00Z"));
        assertThat(captor.getValue().getEndDate()).isEqualTo(LocalDate.parse("2027-01-31"));
        assertThat(captor.getValue().getTimeZone()).isEqualTo("Asia/Seoul");
        assertThat(captor.getValue().getRecurrenceLines()).containsExactlyElementsOf(normalized);
        verify(eventRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("전체 수정은 새 정의 검증 후 master snapshot을 교체하고 기존 override를 제거한다")
    void givenValidUpdate_whenUpdate_thenReplacesMasterAndDeletesOverrides() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Tag tag = tag();
        List<String> normalized = List.of("RRULE:FREQ=WEEKLY;COUNT=2");
        when(recurrenceEventRepository.findByIdAndAccountIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(recurrenceEvent));
        when(tagService.getTagOrDefault(1L, null)).thenReturn(tag);
        when(recurrenceEngine.validate(any(RecurrenceSchedule.class), any())).thenReturn(normalized);
        UpdateRecurrenceEventRequest request = new UpdateRecurrenceEventRequest(
                "Updated",
                null,
                true,
                LocalDate.parse("2027-02-01"),
                LocalDate.parse("2027-02-03"),
                null,
                null,
                null,
                normalized,
                null
        );

        // when
        recurrenceEventService.updateRecurrenceEvent(1L, 10L, request);

        // then
        assertThat(recurrenceEvent.getRecurrenceTitle()).isEqualTo("Updated");
        assertThat(recurrenceEvent.isAllDay()).isTrue();
        assertThat(recurrenceEvent.getTimeZone()).isNull();
        verify(recurrenceEventOverrideRepository).deleteByRecurrenceEvent_Id(10L);
    }

    @Test
    @DisplayName("occurrence PATCH는 title과 null description을 포함한 완전한 snapshot을 저장한다")
    void givenOccurrencePatch_whenUpdate_thenStoresCompleteSnapshot() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Instant originStartAt = recurrenceEvent.getStartAt();
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
                Instant.parse("2027-01-03T03:00:00Z")
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

    private CreateRecurrenceEventRequest timedCreateRequest() {
        return new CreateRecurrenceEventRequest(
                "Rule",
                "memo",
                false,
                LocalDate.parse("2027-01-01"),
                LocalDate.parse("2027-01-31"),
                LocalTime.parse("09:00:00"),
                LocalTime.parse("10:00:00"),
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
                        LocalDate.parse("2027-01-01"),
                        LocalDate.parse("2027-01-01"),
                        LocalTime.parse("09:00:00"),
                        LocalTime.parse("10:00:00"),
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
        return new Tag(TagType.DEFAULT, "기타", "#64748B");
    }

    private Account account() {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", 1L);
        return account;
    }
}
