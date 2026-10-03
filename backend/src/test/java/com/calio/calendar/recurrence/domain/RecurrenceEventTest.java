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
    RecurrenceEventOverride original = event.changeOccurrence(ORIGIN, true, "First", null, MOVED);

    // when
    RecurrenceEventOverride changed =
        event.changeOccurrence(ORIGIN, false, "Updated", "memo", MOVED);

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
    RecurrenceEventOverride original = event.changeOccurrence(ORIGIN, true, "Moved", null, MOVED);
    event.update(
        "Updated series",
        null,
        RecurrenceSchedule.create(
            false, ORIGIN.plusSeconds(86400), ORIGIN.plusSeconds(90000), "UTC"),
        List.of("RRULE:FREQ=WEEKLY"),
        2L);

    // when
    RecurrenceEventOverride excluded =
        event.excludeOccurrence(ORIGIN, false, ORIGIN.plusSeconds(86400));

    // then
    assertThat(excluded).isSameAs(original);
    assertThat(excluded.isDeleted()).isTrue();
    assertThat(excluded.getOverrideTitle()).isNull();
    assertThat(excluded.getOverrideStartAt()).isNull();

    // when
    RecurrenceEventOverride restored =
        event.changeOccurrence(ORIGIN, false, "Restored", null, MOVED);

    // then
    assertThat(restored).isSameAs(original);
    assertThat(restored.isDeleted()).isFalse();
    assertThat(restored.getDeletedAt()).isNull();
    assertThat(event.getOverrides()).containsExactly(original);
  }

  @Test
  @DisplayName("기존 개별 변경과 생성 가능한 회차가 모두 없으면 변경·제외를 거절하고 상태를 추가하지 않는다")
  void unknownOriginCannotCreateOverride() {
    // given
    RecurrenceEvent event = recurrenceEvent();

    // when, then
    assertThatThrownBy(() -> event.changeOccurrence(ORIGIN, false, "Unknown", null, MOVED))
        .isInstanceOf(CalioException.class)
        .extracting(exception -> ((CalioException) exception).getErrorCode())
        .isEqualTo(ErrorCode.RECURRENCE_OCCURRENCE_NOT_FOUND);
    assertThatThrownBy(() -> event.excludeOccurrence(ORIGIN, false, ORIGIN))
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
    RecurrenceEventOverride active = first.changeOccurrence(ORIGIN, true, "Moved", null, MOVED);

    // when
    RecurrenceEventOverride excluded = second.excludeOccurrence(ORIGIN, true, ORIGIN);

    // then
    assertThat(active).isNotSameAs(excluded);
    assertThat(first.findOverride(ORIGIN)).containsSame(active);
    assertThat(second.findOverride(ORIGIN)).containsSame(excluded);
    assertThat(active.isDeleted()).isFalse();
    assertThat(excluded.isDeleted()).isTrue();
  }

  @Test
  @DisplayName("외부 제공자의 개별 변경도 Root가 같은 원래 시작값으로 갱신하고 제외한다")
  void providerChangesAreOwnedByRoot() {
    // given
    RecurrenceEvent event = recurrenceEvent();
    RecurrenceEventOverride original = event.excludeProviderOccurrence(ORIGIN, ORIGIN);

    // when
    RecurrenceEventOverride active =
        event.updateProviderOccurrence(ORIGIN, "Provider change", null, MOVED);

    // then
    assertThat(active).isSameAs(original);
    assertThat(active.isDeleted()).isFalse();
    assertThat(event.getOverrides()).containsExactly(original);
  }

  @Test
  @DisplayName("Root가 노출한 목록으로 자식을 추가·제거할 수 없다")
  void exposedOverridesCannotBypassRoot() {
    // given
    RecurrenceEvent event = recurrenceEvent();
    RecurrenceEventOverride override = event.changeOccurrence(ORIGIN, true, "Moved", null, MOVED);

    // when, then
    assertThatThrownBy(() -> event.getOverrides().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(event.getOverrides()).containsExactly(override);
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
