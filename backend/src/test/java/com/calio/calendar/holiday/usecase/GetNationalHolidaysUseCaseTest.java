package com.calio.calendar.holiday.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.holiday.domain.NationalHoliday;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetNationalHolidaysUseCaseTest {

  @Mock private NationalHolidayRepository nationalHolidayRepository;

  @InjectMocks private GetNationalHolidaysUseCase getNationalHolidaysUseCase;

  @Test
  @DisplayName("공휴일 조회 UseCase는 날짜와 제목 순서의 repository 결과를 응답으로 변환한다")
  void givenValidRange_whenGet_thenReturnsOrderedResponses() {
    LocalDate from = LocalDate.of(2026, 5, 5);
    LocalDate to = LocalDate.of(2026, 6, 6);
    when(nationalHolidayRepository.findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(
            from, to))
        .thenReturn(List.of(new NationalHoliday(from, "어린이날")));

    var result = getNationalHolidaysUseCase.get(from, to);

    assertThat(result)
        .singleElement()
        .satisfies(
            holiday -> {
              assertThat(holiday.holidayDate()).isEqualTo(from);
              assertThat(holiday.holidayTitle()).isEqualTo("어린이날");
            });
    verify(nationalHolidayRepository)
        .findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(from, to);
  }

  @Test
  @DisplayName("시작일이 종료일보다 늦으면 공휴일 조회 UseCase는 INVALID_TIME_RANGE를 반환한다")
  void givenFromAfterTo_whenGet_thenRejectsRange() {
    LocalDate from = LocalDate.of(2026, 6, 6);
    LocalDate to = LocalDate.of(2026, 5, 5);

    assertThatThrownBy(() -> getNationalHolidaysUseCase.get(from, to))
        .isInstanceOfSatisfying(
            CalioException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
    verifyNoInteractions(nationalHolidayRepository);
  }
}
