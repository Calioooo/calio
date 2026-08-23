package com.calio.calendar.groupcalendar.sharing.event.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonalEventGroupShareTest {

    private static final Instant START_AT = Instant.parse("2028-01-01T09:00:00Z");
    private static final Instant END_AT = Instant.parse("2028-01-01T10:00:00Z");

    @Test
    @DisplayName("새 공유 mapping은 원본 상세를 숨기고 모든 표현 값을 원본에서 상속한다")
    void givenNewShare_whenCreated_thenInheritsSourceRepresentation() {
        // when
        PersonalEventGroupShare share = new PersonalEventGroupShare(event(), null);

        // then
        assertThat(share.isShowOriginalDetails()).isFalse();
        assertThat(share.getOverrideTitle()).isNull();
        assertThat(share.getOverrideStartAt()).isNull();
        assertThat(share.getOverrideEndAt()).isNull();
        assertThat(share.getOverrideAllDay()).isNull();
    }

    @Test
    @DisplayName("원본 상세를 숨겨도 명시적 제목과 일정 표현 override를 저장할 수 있다")
    void givenAnonymousShare_whenUpdateRepresentation_thenKeepsExplicitOverrides() {
        // given
        PersonalEventGroupShare share = new PersonalEventGroupShare(event(), null);
        Instant overrideStartAt = Instant.parse("2028-01-01T11:00:00Z");
        Instant overrideEndAt = Instant.parse("2028-01-01T12:00:00Z");

        // when
        share.updateRepresentation(
                false,
                "개인 일정",
                overrideStartAt,
                overrideEndAt,
                false
        );

        // then
        assertThat(share.isShowOriginalDetails()).isFalse();
        assertThat(share.getOverrideTitle()).isEqualTo("개인 일정");
        assertThat(share.getOverrideStartAt()).isEqualTo(overrideStartAt);
        assertThat(share.getOverrideEndAt()).isEqualTo(overrideEndAt);
        assertThat(share.getOverrideAllDay()).isFalse();
    }

    @Test
    @DisplayName("원본과 합성한 공유 일정이 유효하지 않으면 표현 override를 저장할 수 없다")
    void givenInvalidEffectiveSchedule_whenUpdateRepresentation_thenThrowsInvalidTimeRange() {
        // given
        PersonalEventGroupShare share = new PersonalEventGroupShare(event(), null);

        // when, then
        assertThatThrownBy(() -> share.updateRepresentation(
                false,
                null,
                END_AT,
                START_AT,
                null
        )).isInstanceOfSatisfying(
                CalioException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME_RANGE)
        );
    }

    private Event event() {
        return new Event(
                "원본 일정",
                "원본 설명",
                START_AT,
                END_AT,
                false,
                "UTC",
                null,
                null,
                null
        );
    }
}
