package com.calio.calendar.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VoteCandidateDateRangeTest {

  private static final LocalDate START_DATE = LocalDate.of(2026, 8, 14);

  @Test
  @DisplayName("후보 기간은 시작일을 포함해 최대 31일까지 허용한다")
  void givenThirtyOneDayRange_whenCreate_thenCreatesCandidateDateRange() {
    VoteCandidateDateRange candidateDateRange =
        VoteCandidateDateRange.of(START_DATE, START_DATE.plusDays(30));

    assertThat(candidateDateRange.contains(START_DATE)).isTrue();
    assertThat(candidateDateRange.contains(START_DATE.plusDays(30))).isTrue();
    assertThat(candidateDateRange.contains(START_DATE.plusDays(31))).isFalse();
  }

  @Test
  @DisplayName("후보 종료일이 시작일보다 빠르거나 31일을 초과하면 생성할 수 없다")
  void givenInvalidRange_whenCreate_thenRejects() {
    assertThatThrownBy(() -> VoteCandidateDateRange.of(START_DATE, START_DATE.minusDays(1)))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    assertThatThrownBy(() -> VoteCandidateDateRange.of(START_DATE, START_DATE.plusDays(31)))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }
}
