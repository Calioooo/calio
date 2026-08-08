package com.calio.calendar.holiday.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.calio.calendar.holiday.controller.dto.NationalHolidayResponse;
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
class NationalHolidayQueryServiceTest {

    @Mock
    private NationalHolidayRepository nationalHolidayRepository;

    @InjectMocks
    private NationalHolidayQueryService nationalHolidayQueryService;

    @Test
    @DisplayName("공휴일 조회는 날짜와 제목 오름차순 repository 결과를 응답 DTO로 변환한다")
    void givenDateRange_whenGetNationalHolidays_thenMapsOrderedRepositoryResult() {
        // given
        LocalDate from = LocalDate.of(2026, 5, 5);
        LocalDate to = LocalDate.of(2026, 6, 6);
        NationalHoliday alternateHoliday = new NationalHoliday(from, "대체공휴일");
        NationalHoliday childrensDay = new NationalHoliday(from, "어린이날");
        NationalHoliday memorialDay = new NationalHoliday(to, "현충일");
        given(nationalHolidayRepository
                .findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(from, to))
                .willReturn(List.of(alternateHoliday, childrensDay, memorialDay));

        // when
        List<NationalHolidayResponse> responses = nationalHolidayQueryService.getNationalHolidays(from, to);

        // then
        assertThat(responses)
                .extracting(NationalHolidayResponse::holidayDate, NationalHolidayResponse::holidayTitle)
                .containsExactly(
                        tuple(from, "대체공휴일"),
                        tuple(from, "어린이날"),
                        tuple(to, "현충일")
                );
        verify(nationalHolidayRepository)
                .findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(from, to);
    }
}
