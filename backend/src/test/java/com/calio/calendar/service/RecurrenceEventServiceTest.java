package com.calio.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.controller.dto.CreateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.EventResponse;
import com.calio.calendar.controller.dto.UpdateRecurrenceEventRequest;
import com.calio.calendar.controller.dto.UpdateRecurrenceOccurrenceRequest;
import com.calio.calendar.exception.CalioException;
import com.calio.calendar.exception.ErrorCode;
import com.calio.calendar.repository.AccountRepository;
import com.calio.calendar.repository.EventRepository;
import com.calio.calendar.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.repository.RecurrenceEventRepository;
import com.calio.calendar.repository.entity.Account;
import com.calio.calendar.repository.entity.RecurrenceEvent;
import com.calio.calendar.repository.entity.RecurrenceEventOverride;
import com.calio.calendar.repository.entity.RecurrenceFrequency;
import com.calio.calendar.repository.entity.Tag;
import com.calio.calendar.repository.entity.TagType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

    @InjectMocks
    private RecurrenceEventService recurrenceEventService;

    @Test
    @DisplayName("반복 일정 생성은 RecurrenceEvent rule만 저장하고 occurrence Event row를 만들지 않는다")
    void givenCreateRequest_whenCreateRecurrenceEvent_thenDoesNotMaterializeOccurrenceEvents() {
        // given
        Tag tag = tag();
        when(tagService.getTagOrDefault(1L, null)).thenReturn(tag);
        when(recurrenceEventRepository.save(any(RecurrenceEvent.class)))
                .thenAnswer(invocation -> {
                    RecurrenceEvent recurrenceEvent = invocation.getArgument(0);
                    ReflectionTestUtils.setField(recurrenceEvent, "id", 10L);
                    return recurrenceEvent;
                });
        CreateRecurrenceEventRequest request = new CreateRecurrenceEventRequest(
                "Rule",
                "memo",
                LocalDate.parse("2027-01-01"),
                LocalDate.parse("2027-01-02"),
                LocalTime.parse("09:00:00"),
                LocalTime.parse("10:00:00"),
                RecurrenceFrequency.DAILY,
                null
        );

        // when
        recurrenceEventService.createRecurrenceEvent(1L, request);

        // then
        verify(recurrenceEventRepository).save(any(RecurrenceEvent.class));
        verify(eventRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("반복 일정 전체 수정은 rule을 갱신하고 해당 recurrence override를 hard-delete한다")
    void givenUpdateRequest_whenUpdateRecurrenceEvent_thenDeletesOverridesWithoutRebuildingEvents() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent("Original", "2027-02-01", "2027-02-02");
        Tag fallbackTag = tag();
        when(recurrenceEventRepository.findByIdAndAccount_Id(10L, 1L)).thenReturn(Optional.of(recurrenceEvent));
        when(tagService.getTagOrDefault(1L, null)).thenReturn(fallbackTag);
        UpdateRecurrenceEventRequest request = new UpdateRecurrenceEventRequest(
                "Updated",
                null,
                LocalDate.parse("2027-02-03"),
                LocalDate.parse("2027-02-10"),
                LocalTime.parse("11:00:00"),
                LocalTime.parse("12:00:00"),
                RecurrenceFrequency.WEEKLY,
                null
        );

        // when
        recurrenceEventService.updateRecurrenceEvent(1L, 10L, request);

        // then
        assertThat(recurrenceEvent.getRecurrenceTitle()).isEqualTo("Updated");
        assertThat(recurrenceEvent.getRecurrenceStartDate()).isEqualTo(LocalDate.parse("2027-02-03"));
        assertThat(recurrenceEvent.getRecurrenceEndDate()).isEqualTo(LocalDate.parse("2027-02-10"));
        assertThat(recurrenceEvent.getRecurrenceStartTime()).isEqualTo(LocalTime.parse("11:00:00"));
        assertThat(recurrenceEvent.getRecurrenceEndTime()).isEqualTo(LocalTime.parse("12:00:00"));
        assertThat(recurrenceEvent.getRecurrenceFrequency()).isEqualTo(RecurrenceFrequency.WEEKLY);
        assertThat(recurrenceEvent.getTag()).isSameAs(fallbackTag);
        verify(recurrenceEventOverrideRepository).deleteByRecurrenceEvent_Id(10L);
        verify(eventRepository, never()).saveAll(any());
        verify(eventRepository, never()).deleteAll(any(Iterable.class));
    }

    @Test
    @DisplayName("단일 occurrence PATCH는 recurrenceId와 originStartAt으로 modified override를 생성하고 가상 EventResponse를 반환한다")
    void givenValidOccurrencePatch_whenUpdateRecurrenceOccurrence_thenCreatesModifiedOverride() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent("Rule", "2027-03-01", "2027-03-02");
        Instant originStartAt = Instant.parse("2027-03-01T09:00:00Z");
        Instant movedStartAt = Instant.parse("2027-03-10T12:00:00Z");
        Instant movedEndAt = Instant.parse("2027-03-10T13:00:00Z");
        when(recurrenceEventRepository.findByIdAndAccount_Id(10L, 1L)).thenReturn(Optional.of(recurrenceEvent));
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt))
                .thenReturn(Optional.empty());
        when(recurrenceEventOverrideRepository.save(any(RecurrenceEventOverride.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest(
                originStartAt,
                movedStartAt,
                movedEndAt
        );

        // when
        EventResponse response = recurrenceEventService.updateRecurrenceOccurrence(1L, 10L, request);

        // then
        ArgumentCaptor<RecurrenceEventOverride> overrideCaptor =
                ArgumentCaptor.forClass(RecurrenceEventOverride.class);
        verify(recurrenceEventOverrideRepository).save(overrideCaptor.capture());
        assertThat(overrideCaptor.getValue().getRecurrenceEvent()).isSameAs(recurrenceEvent);
        assertThat(overrideCaptor.getValue().getOriginStartAt()).isEqualTo(originStartAt);
        assertThat(response.id()).isNull();
        assertThat(response.recurrenceId()).isEqualTo(10L);
        assertThat(response.originStartAt()).isEqualTo(originStartAt);
        assertThat(response.startAt()).isEqualTo(movedStartAt);
        assertThat(response.endAt()).isEqualTo(movedEndAt);
    }

    @Test
    @DisplayName("deleted override 대상 PATCH는 occurrence를 복원하지 않고 RECURRENCE_OCCURRENCE_NOT_FOUND를 던진다")
    void givenDeletedOverride_whenUpdateRecurrenceOccurrence_thenThrowsOccurrenceNotFound() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent("Rule", "2027-04-01", "2027-04-01");
        Instant originStartAt = Instant.parse("2027-04-01T09:00:00Z");
        RecurrenceEventOverride deletedOverride = RecurrenceEventOverride.deleted(
                recurrenceEvent,
                originStartAt,
                Instant.parse("2027-04-01T11:00:00Z")
        );
        when(recurrenceEventRepository.findByIdAndAccount_Id(10L, 1L)).thenReturn(Optional.of(recurrenceEvent));
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt))
                .thenReturn(Optional.of(deletedOverride));

        UpdateRecurrenceOccurrenceRequest request = new UpdateRecurrenceOccurrenceRequest(
                originStartAt,
                Instant.parse("2027-04-01T12:00:00Z"),
                Instant.parse("2027-04-01T13:00:00Z")
        );

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.updateRecurrenceOccurrence(1L, 10L, request))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("단일 occurrence DELETE는 modified override를 deletion override 상태로 전환한다")
    void givenModifiedOverride_whenDeleteRecurrenceOccurrence_thenConvertsToDeletedOverride() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent("Rule", "2027-05-01", "2027-05-01");
        Instant originStartAt = Instant.parse("2027-05-01T09:00:00Z");
        RecurrenceEventOverride override = RecurrenceEventOverride.modified(
                recurrenceEvent,
                originStartAt,
                Instant.parse("2027-05-02T09:00:00Z"),
                Instant.parse("2027-05-02T10:00:00Z")
        );
        when(recurrenceEventRepository.findByIdAndAccount_Id(10L, 1L)).thenReturn(Optional.of(recurrenceEvent));
        when(recurrenceEventOverrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(10L, originStartAt))
                .thenReturn(Optional.of(override));

        // when
        recurrenceEventService.deleteRecurrenceOccurrence(1L, 10L, originStartAt);

        // then
        assertThat(override.getDeletedAt()).isNotNull();
        assertThat(override.getOverrideStartAt()).isNull();
        assertThat(override.getOverrideEndAt()).isNull();
        verify(recurrenceEventOverrideRepository).flush();
    }

    @Test
    @DisplayName("존재하지 않는 recurrenceId의 단일 occurrence DELETE는 override repository를 호출하지 않는다")
    void givenMissingRecurrenceId_whenDeleteRecurrenceOccurrence_thenThrowsRecurrenceEventNotFound() {
        // given
        when(recurrenceEventRepository.findByIdAndAccount_Id(10L, 1L)).thenReturn(Optional.empty());

        // when, then
        assertThatThrownBy(() -> recurrenceEventService.deleteRecurrenceOccurrence(
                1L,
                10L,
                Instant.parse("2027-06-01T09:00:00Z")
        ))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECURRENCE_EVENT_NOT_FOUND)
                );
        verifyNoInteractions(recurrenceEventOverrideRepository);
    }

    private RecurrenceEvent recurrenceEvent(String title, String startDate, String endDate) {
        RecurrenceEvent recurrenceEvent = new RecurrenceEvent(
                title,
                "memo",
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                LocalTime.parse("09:00:00"),
                LocalTime.parse("10:00:00"),
                RecurrenceFrequency.DAILY,
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
