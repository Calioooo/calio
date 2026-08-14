package com.calio.calendar.holiday.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class NationalHolidayQueryServiceTest {

    @Mock
    private NationalHolidayRepository nationalHolidayRepository;

    @InjectMocks
    private NationalHolidayQueryService nationalHolidayQueryService;

    @Test
    @DisplayName("QueryService의 모든 조회는 readOnly 트랜잭션 경계 안에서 실행한다")
    void queryServiceUsesReadOnlyTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                NationalHolidayQueryService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    @DisplayName("공휴일 조회는 날짜와 제목 오름차순 repository 결과를 그대로 반환한다")
    void givenDateRange_whenGetNationalHolidays_thenReturnsOrderedRepositoryResult() {
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
        List<NationalHoliday> result = nationalHolidayQueryService.getNationalHolidays(from, to);

        // then
        assertThat(result).containsExactly(alternateHoliday, childrensDay, memorialDay);
        verify(nationalHolidayRepository)
                .findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(from, to);
    }

    @Test
    @DisplayName("snapshot 비교용 기존 공휴일은 요청 연도의 첫날부터 마지막 날까지 조회한다")
    void givenYear_whenFindExistingHolidays_thenQueriesExactYearRange() {
        // given
        LocalDate firstDay = LocalDate.of(2026, 1, 1);
        LocalDate lastDay = LocalDate.of(2026, 12, 31);
        List<NationalHoliday> holidays = List.of(new NationalHoliday(firstDay, "신정"));
        given(nationalHolidayRepository.findByHolidayDateBetween(firstDay, lastDay))
                .willReturn(holidays);

        // when
        List<NationalHoliday> result = nationalHolidayQueryService.getHolidaysInYear(2026);

        // then
        assertThat(result).isSameAs(holidays);
        verify(nationalHolidayRepository).findByHolidayDateBetween(firstDay, lastDay);
    }
}
