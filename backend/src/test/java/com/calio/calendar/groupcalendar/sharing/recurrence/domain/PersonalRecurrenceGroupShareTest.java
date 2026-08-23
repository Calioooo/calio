package com.calio.calendar.groupcalendar.sharing.recurrence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonalRecurrenceGroupShareTest {

    private static final Instant SOURCE_START_AT = Instant.parse("2028-01-01T09:00:00Z");
    private static final Instant SOURCE_END_AT = Instant.parse("2028-01-01T10:00:00Z");
    private static final Instant MASTER_START_AT = Instant.parse("2028-01-01T11:00:00Z");
    private static final Instant MASTER_END_AT = Instant.parse("2028-01-08T14:00:00Z");
    private static final Instant OCCURRENCE_START_AT = Instant.parse("2028-01-08T13:00:00Z");
    private static final Instant ORIGIN_START_AT = Instant.parse("2028-01-08T09:00:00Z");

    @Test
    @DisplayName("새 반복 공유 mapping은 원본 상세를 숨기고 모든 표현 값을 원본에서 상속한다")
    void givenNewShare_whenCreated_thenInheritsSourceRepresentation() {
        // when
        PersonalRecurrenceGroupShare share = share();

        // then
        assertThat(share.isShowOriginalDetails()).isFalse();
        assertThat(share.getOverrideTitle()).isNull();
        assertThat(share.resolvePublicTitle("원본 반복 일정", "닉네임의 일정"))
                .isEqualTo("닉네임의 일정");
        assertThat(share.resolveStartAt(SOURCE_START_AT)).isEqualTo(SOURCE_START_AT);
        assertThat(share.resolveEndAt(SOURCE_END_AT)).isEqualTo(SOURCE_END_AT);
        assertThat(share.resolveAllDay(false)).isFalse();
    }

    @Test
    @DisplayName("원본 상세를 숨겨도 반복 공유의 명시적 제목을 공개할 수 있다")
    void givenAnonymousShare_whenUpdateRepresentation_thenKeepsExplicitTitle() {
        // given
        PersonalRecurrenceGroupShare share = share();

        // when
        share.updateRepresentation(false, "공개 제목", null, null, null);

        // then
        assertThat(share.isShowOriginalDetails()).isFalse();
        assertThat(share.resolvePublicTitle("원본 반복 일정", "닉네임의 일정"))
                .isEqualTo("공개 제목");
    }

    @Test
    @DisplayName("원본 상세 공개를 활성화한 반복 공유는 원본 제목을 공개한다")
    void givenDetailVisibleShare_whenResolvePublicTitle_thenUsesSourceTitle() {
        // given
        PersonalRecurrenceGroupShare share = share();
        share.updateRepresentation(true, null, null, null, null);

        // when
        String title = share.resolvePublicTitle("원본 반복 일정", "닉네임의 일정");

        // then
        assertThat(title).isEqualTo("원본 반복 일정");
    }

    @Test
    @DisplayName("occurrence 표현 override는 반복 공유의 기본 표현보다 우선한다")
    void givenMasterAndOccurrenceOverrides_whenResolveRepresentation_thenOccurrenceTakesPrecedence() {
        // given
        PersonalRecurrenceGroupShare share = share();
        share.updateRepresentation(false, "기본 공개 제목", MASTER_START_AT, MASTER_END_AT, false);
        PersonalRecurrenceGroupShareOccurrenceOverride occurrenceOverride =
                new PersonalRecurrenceGroupShareOccurrenceOverride(share, ORIGIN_START_AT);

        // when
        occurrenceOverride.updateRepresentation("회차 공개 제목", OCCURRENCE_START_AT, null, false);

        // then
        assertThat(occurrenceOverride.resolvePublicTitle("원본 반복 일정", "닉네임의 일정"))
                .isEqualTo("회차 공개 제목");
        assertThat(occurrenceOverride.resolveStartAt(SOURCE_START_AT)).isEqualTo(OCCURRENCE_START_AT);
        assertThat(occurrenceOverride.resolveEndAt(SOURCE_END_AT)).isEqualTo(MASTER_END_AT);
        assertThat(occurrenceOverride.resolveAllDay(false)).isFalse();
    }

    @Test
    @DisplayName("원본과 합성한 반복 공유 일정이 유효하지 않으면 표현 override를 저장할 수 없다")
    void givenInvalidEffectiveSchedule_whenUpdateRepresentation_thenThrowsInvalidTimeRange() {
        // given
        PersonalRecurrenceGroupShare share = share();
        PersonalRecurrenceGroupShareOccurrenceOverride occurrenceOverride =
                new PersonalRecurrenceGroupShareOccurrenceOverride(share, ORIGIN_START_AT);

        // when, then
        assertThatThrownBy(() -> share.updateRepresentation(false, null, SOURCE_END_AT, null, null))
                .isInstanceOfSatisfying(
                        CalioException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_TIME_RANGE)
                );
        assertThatThrownBy(() -> occurrenceOverride.updateRepresentation(
                null,
                SOURCE_END_AT,
                null,
                null
        )).isInstanceOfSatisfying(
                CalioException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME_RANGE)
        );
    }

    private PersonalRecurrenceGroupShare share() {
        return new PersonalRecurrenceGroupShare(
                new RecurrenceEvent(
                        "원본 반복 일정",
                        "원본 설명",
                        new RecurrenceSchedule(SOURCE_START_AT, SOURCE_END_AT, false, "UTC"),
                        List.of("RRULE:FREQ=WEEKLY"),
                        null,
                        null
                ),
                null,
                PersonalRecurrenceGroupShareScope.WHOLE_SERIES
        );
    }
}
