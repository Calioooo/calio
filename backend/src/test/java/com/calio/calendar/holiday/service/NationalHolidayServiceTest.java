package com.calio.calendar.holiday.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.holiday.controller.dto.NationalHolidayResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NationalHolidayServiceTest {

    @Mock
    private NationalHolidayQueryService nationalHolidayQueryService;

    @InjectMocks
    private NationalHolidayService nationalHolidayService;

    @Test
    @DisplayName("유효한 날짜 범위의 공휴일 조회는 QueryService에 위임한다")
    void givenValidDateRange_whenGetNationalHolidays_thenDelegatesQuery() {
        // given
        LocalDate from = LocalDate.of(2026, 5, 5);
        LocalDate to = LocalDate.of(2026, 6, 6);
        List<NationalHolidayResponse> expected = List.of(
                new NationalHolidayResponse(1L, from, "어린이날"),
                new NationalHolidayResponse(2L, to, "현충일")
        );
        given(nationalHolidayQueryService.getNationalHolidays(from, to)).willReturn(expected);

        // when
        List<NationalHolidayResponse> actual = nationalHolidayService.getNationalHolidays(from, to);

        // then
        assertThat(actual).isEqualTo(expected);
        verify(nationalHolidayQueryService).getNationalHolidays(from, to);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 QueryService를 호출하지 않고 INVALID_TIME_RANGE로 실패한다")
    void givenFromAfterTo_whenGetNationalHolidays_thenRejectsDateRange() {
        // given
        LocalDate from = LocalDate.of(2026, 6, 6);
        LocalDate to = LocalDate.of(2026, 5, 5);

        // when, then
        assertThatThrownBy(() -> nationalHolidayService.getNationalHolidays(from, to))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_TIME_RANGE));
        verifyNoInteractions(nationalHolidayQueryService);
    }
}
