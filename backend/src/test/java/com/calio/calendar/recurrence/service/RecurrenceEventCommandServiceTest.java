package com.calio.calendar.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceEventOverride;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventOverrideRepository;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class RecurrenceEventCommandServiceTest {

    @Mock
    private RecurrenceEventRepository recurrenceEventRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RecurrenceEventOverrideRepository recurrenceEventOverrideRepository;

    @InjectMocks
    private RecurrenceEventCommandService recurrenceEventCommandService;

    @Test
    @DisplayName("CommandService의 모든 상태 변경은 트랜잭션 경계 안에서 실행한다")
    void commandServiceUsesTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                RecurrenceEventCommandService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    @DisplayName("occurrence 수정 command는 전달받은 override를 변경하지 않고 저장한다")
    void givenActiveOverride_whenUpdateOccurrence_thenOnlySavesExactOverride() {
        // given
        RecurrenceEvent recurrenceEvent = recurrenceEvent();
        Instant originStartAt = Instant.parse("2027-01-01T00:00:00Z");
        RecurrenceEventOverride override = RecurrenceEventOverride.active(
                recurrenceEvent,
                originStartAt,
                "Updated occurrence",
                null,
                CanonicalSchedule.recurrenceOverride(
                        Instant.parse("2027-01-03T02:00:00Z"),
                        Instant.parse("2027-01-03T03:00:00Z"),
                        false,
                        "Asia/Seoul"
                )
        );
        when(recurrenceEventOverrideRepository.saveAndFlush(override)).thenReturn(override);

        // when
        RecurrenceEventOverride result = recurrenceEventCommandService.updateRecurrenceOccurrence(override);

        // then
        verify(recurrenceEventOverrideRepository).saveAndFlush(override);
        assertThat(result).isSameAs(override);
        assertThat(override.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("occurrence 삭제 command는 전달받은 override를 변경하지 않고 저장한다")
    void givenDeletedOverride_whenDeleteOccurrence_thenOnlySavesExactOverride() {
        // given
        RecurrenceEventOverride override = RecurrenceEventOverride.deleted(
                recurrenceEvent(),
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-01-02T00:00:00Z")
        );

        // when
        recurrenceEventCommandService.deleteRecurrenceOccurrence(override);

        // then
        verify(recurrenceEventOverrideRepository).saveAndFlush(override);
        assertThat(override.isDeleted()).isTrue();
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
                new Tag(TagType.DEFAULT, "기타", "#64748B"),
                new Account()
        );
        ReflectionTestUtils.setField(recurrenceEvent, "id", 10L);
        return recurrenceEvent;
    }
}
