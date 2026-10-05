package com.calio.calendar.recurrence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.domain.CanonicalSchedule;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecurrenceEventTest {

  private static final Instant ORIGIN = Instant.parse("2027-01-01T09:00:00Z");
  private static final CanonicalSchedule MOVED =
      CanonicalSchedule.recurrenceOverride(
          ORIGIN.plusSeconds(7200), ORIGIN.plusSeconds(10800), false, "UTC");

  @Test
  @DisplayName("같은 원래 시작값을 다시 변경하면 자식을 추가하지 않고 기존 개별 변경을 수정한다")
  void changeSameOriginUpdatesOwnedOverride() {
    // given
    RecurrenceEvent event = recurrenceEvent();
    RecurrenceEventOverride original = event.changeOccurrence(ORIGIN, "First", null, MOVED);

    // when
    RecurrenceEventOverride changed = event.changeOccurrence(ORIGIN, "Updated", "memo", MOVED);

    // then
    assertThat(changed).isSameAs(original);
    assertThat(event.getOverrides()).containsExactly(original);
    assertThat(event.findOverride(ORIGIN)).containsSame(original);
    assertThat(original.getOverrideTitle()).isEqualTo("Updated");
    assertThat(original.getOverrideDescription()).isEqualTo("memo");
    assertThat(original.getOriginStartAt()).isEqualTo(ORIGIN);
    assertThat(original.getOverrideStartAt()).isEqualTo(MOVED.startAt());
  }

  @Test
  @DisplayName("시리즈 규칙이 바뀌어도 기존 개별 변경의 제외·복원은 같은 자식과 원래 시작값을 유지한다")
  void restoreExistingOverrideAfterMasterRuleChangeKeepsIdentity() {
    // given
    RecurrenceEvent event = recurrenceEvent();
    RecurrenceEventOverride original = event.changeOccurrence(ORIGIN, "Moved", null, MOVED);
    event.update(
        "Updated series",
        null,
        RecurrenceSchedule.create(
            false, ORIGIN.plusSeconds(86400), ORIGIN.plusSeconds(90000), "UTC"),
        List.of("RRULE:FREQ=WEEKLY"),
        2L);

    // when
    RecurrenceEventOverride excluded = event.excludeOccurrence(ORIGIN, ORIGIN.plusSeconds(86400));

    // then
    assertThat(excluded).isSameAs(original);
    assertThat(excluded.isDeleted()).isTrue();
    assertThat(excluded.getOverrideTitle()).isNull();
    assertThat(excluded.getOverrideStartAt()).isNull();

    // when
    RecurrenceEventOverride restored = event.changeOccurrence(ORIGIN, "Restored", null, MOVED);

    // then
    assertThat(restored).isSameAs(original);
    assertThat(restored.isDeleted()).isFalse();
    assertThat(restored.getDeletedAt()).isNull();
    assertThat(event.getOverrides()).containsExactly(original);
  }

  @Test
  @DisplayName("기존 개별 변경과 반복 규칙에 모두 없는 원래 시작값은 작업 대상 회차로 확인할 수 없다")
  void unknownOriginCannotBeResolved() {
    // given
    RecurrenceEvent event = recurrenceEvent();

    // when, then
    RecurrenceOriginMatcher originMatcher = (schedule, rules, origin) -> false;
    assertThatThrownBy(() -> event.requireOccurrence(ORIGIN, originMatcher))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
    assertThat(event.getOverrides()).isEmpty();
  }

  @Test
  @DisplayName("동일한 원래 시작값이라도 시리즈가 다르면 각 Root가 자신의 개별 변경을 소유한다")
  void sameOriginInDifferentRootsHasIndependentState() {
    // given
    RecurrenceEvent first = recurrenceEvent();
    RecurrenceEvent second = recurrenceEvent();
    RecurrenceEventOverride active = first.changeOccurrence(ORIGIN, "Moved", null, MOVED);

    // when
    RecurrenceEventOverride excluded = second.excludeOccurrence(ORIGIN, ORIGIN);

    // then
    assertThat(active).isNotSameAs(excluded);
    assertThat(first.findOverride(ORIGIN)).containsSame(active);
    assertThat(second.findOverride(ORIGIN)).containsSame(excluded);
    assertThat(active.isDeleted()).isFalse();
    assertThat(excluded.isDeleted()).isTrue();
  }

  @Test
  @DisplayName("현재 규칙에 없는 회차 기록도 같은 변경 메서드로 제외·복원하고 원래 시작값을 유지한다")
  void recordedOccurrenceOutsideCurrentRuleKeepsLocalIdentity() {
    // given
    RecurrenceEvent event = recurrenceEvent();
    Instant outsideOrigin = ORIGIN.plusSeconds(1);
    RecurrenceEventOverride original = event.excludeOccurrence(outsideOrigin, ORIGIN);

    // when
    RecurrenceEventOverride active =
        event.changeOccurrence(outsideOrigin, "Recorded change", null, MOVED);

    // then
    assertThat(active).isSameAs(original);
    assertThat(active.isDeleted()).isFalse();
    assertThat(active.getOriginStartAt()).isEqualTo(outsideOrigin);
    assertThat(event.getOverrides()).containsExactly(original);
  }

  @Test
  @DisplayName("보존된 제외 기록은 현재 규칙 조회 없이 작업 대상 회차로 확인한다")
  void excludedOverrideCanBeResolvedWithoutCurrentRule() {
    // given
    RecurrenceEvent event = recurrenceEvent();
    RecurrenceEventOverride excluded = event.excludeOccurrence(ORIGIN, ORIGIN);
    RecurrenceOriginMatcher originMatcher =
        (schedule, rules, origin) -> {
          throw new AssertionError("기존 개별 변경은 현재 규칙 조회가 필요하지 않다.");
        };

    // when
    event.requireOccurrence(ORIGIN, originMatcher);

    // then
    assertThat(event.findOverride(ORIGIN)).containsSame(excluded);
    assertThat(excluded.isDeleted()).isTrue();
  }

  @Test
  @DisplayName("반복 규칙에서 확인한 회차는 작업 대상으로 사용할 수 있고 조회만으로 override를 만들지 않는다")
  void generatedOccurrenceCanBeResolvedWithoutRecordingOverride() {
    // given
    RecurrenceEvent event = recurrenceEvent();
    RecurrenceOriginMatcher originMatcher =
        (schedule, rules, origin) ->
            schedule.equals(RecurrenceSchedule.from(event))
                && rules.equals(event.getRecurrenceRules())
                && origin.equals(ORIGIN);

    // when
    event.requireOccurrence(ORIGIN, originMatcher);

    // then
    assertThat(event.getOverrides()).isEmpty();
  }

  @Test
  @DisplayName("Root가 노출한 목록으로 자식을 추가·제거할 수 없다")
  void exposedOverridesCannotBypassRoot() {
    // given
    RecurrenceEvent event = recurrenceEvent();
    RecurrenceEventOverride override = event.changeOccurrence(ORIGIN, "Moved", null, MOVED);

    // when, then
    assertThatThrownBy(() -> event.getOverrides().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(event.getOverrides()).containsExactly(override);
  }

  @Test
  @DisplayName("필수 변경 시간이 없으면 기존 자식의 제목과 변경 내용을 보존한다")
  void invalidChangeKeepsExistingOverrideState() {
    // given
    RecurrenceEvent event = recurrenceEvent();
    RecurrenceEventOverride original = event.changeOccurrence(ORIGIN, "Original", "memo", MOVED);

    // when, then
    assertThatThrownBy(() -> event.changeOccurrence(ORIGIN, "Changed", null, null))
        .isInstanceOf(NullPointerException.class);
    assertThat(original.getOverrideTitle()).isEqualTo("Original");
    assertThat(original.getOverrideDescription()).isEqualTo("memo");
    assertThat(original.getOverrideStartAt()).isEqualTo(MOVED.startAt());
    assertThat(original.isDeleted()).isFalse();
    assertThat(event.getOverrides()).containsExactly(original);
  }

  @Test
  @DisplayName("제외 시각이 없으면 기존 자식의 활성 상태와 변경 내용을 보존한다")
  void invalidExclusionKeepsExistingOverrideState() {
    // given
    RecurrenceEvent event = recurrenceEvent();
    RecurrenceEventOverride original = event.changeOccurrence(ORIGIN, "Original", "memo", MOVED);

    // when, then
    assertThatThrownBy(() -> event.excludeOccurrence(ORIGIN, null))
        .isInstanceOf(NullPointerException.class);
    assertThat(original.getOverrideTitle()).isEqualTo("Original");
    assertThat(original.getOverrideDescription()).isEqualTo("memo");
    assertThat(original.getOverrideStartAt()).isEqualTo(MOVED.startAt());
    assertThat(original.isDeleted()).isFalse();
    assertThat(event.getOverrides()).containsExactly(original);
  }

  private RecurrenceEvent recurrenceEvent() {
    return new RecurrenceEvent(
        "Series",
        null,
        RecurrenceSchedule.create(false, ORIGIN, ORIGIN.plusSeconds(3600), "UTC"),
        List.of("RRULE:FREQ=DAILY;COUNT=3"),
        2L,
        1L);
  }
}
