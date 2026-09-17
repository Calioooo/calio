package com.calio.calendar.groupcalendar.recurrence.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.common.domain.CanonicalSchedule;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupCalendarRecurrenceOverrideTest {

    private static final Instant ORIGIN_START_AT = Instant.parse("2026-08-01T09:00:00Z");
    private static final CanonicalSchedule SCHEDULE = CanonicalSchedule.recurrenceOverride(
            Instant.parse("2026-08-01T10:00:00Z"),
            Instant.parse("2026-08-01T11:00:00Z"),
            false,
            "Asia/Seoul"
    );

    @Test
    @DisplayName("active override는 삭제되지 않은 상태로 일정을 보관한다")
    void givenActiveOverride_whenCreated_thenIsNotDeletedAndKeepsSchedule() {
        // given, when
        GroupCalendarRecurrenceOverride override = GroupCalendarRecurrenceOverride.active(
                null,
                ORIGIN_START_AT,
                "회의",
                "설명",
                SCHEDULE
        );

        // then
        assertThat(override.isDeleted()).isFalse();
        assertThat(override.getTitle()).isEqualTo("회의");
        assertThat(override.getStartAt()).isEqualTo(SCHEDULE.startAt());
        assertThat(override.getEndAt()).isEqualTo(SCHEDULE.endAt());
    }

    @Test
    @DisplayName("markDeleted override는 사용자 노출 일정을 제거한다")
    void givenActiveOverride_whenMarkDeleted_thenIsDeletedAndClearsSchedule() {
        // given
        GroupCalendarRecurrenceOverride override = GroupCalendarRecurrenceOverride.active(
                null,
                ORIGIN_START_AT,
                "회의",
                "설명",
                SCHEDULE
        );

        // when
        override.markDeleted(Instant.parse("2026-08-01T08:00:00Z"));

        // then
        assertThat(override.isDeleted()).isTrue();
        assertThat(override.getStartAt()).isNull();
        assertThat(override.getTitle()).isNull();
    }

    @Test
    @DisplayName("삭제된 override를 activate하면 다시 사용자에게 노출된다")
    void givenDeletedOverride_whenActivated_thenRestoresActiveState() {
        // given
        GroupCalendarRecurrenceOverride override = GroupCalendarRecurrenceOverride.deleted(
                null,
                ORIGIN_START_AT,
                Instant.parse("2026-08-01T08:00:00Z")
        );

        // when
        override.activate("변경 회의", "변경 설명", SCHEDULE);

        // then
        assertThat(override.isDeleted()).isFalse();
        assertThat(override.getTitle()).isEqualTo("변경 회의");
        assertThat(override.getStartAt()).isEqualTo(SCHEDULE.startAt());
    }
}
