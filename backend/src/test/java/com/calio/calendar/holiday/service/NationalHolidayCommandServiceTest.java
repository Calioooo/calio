package com.calio.calendar.holiday.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verify;

import com.calio.calendar.holiday.domain.NationalHoliday;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class NationalHolidayCommandServiceTest {

    @Mock
    private NationalHolidayRepository nationalHolidayRepository;

    @InjectMocks
    private NationalHolidayCommandService nationalHolidayCommandService;

    @Test
    @DisplayName("CommandService의 모든 상태 변경은 선언적 트랜잭션 경계 안에서 실행한다")
    void commandServiceUsesTransactionBoundary() {
        // when
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                NationalHolidayCommandService.class,
                Transactional.class
        );

        // then
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    @DisplayName("누락된 provider 공휴일 목록은 추가할 entity로 변환해 한 번만 저장한다")
    void givenMissingProviderRows_whenInsertIfMissing_thenOnlyInsertsHolidays() {
        // given
        List<NationalHolidayProviderRow> missingProviderRows = List.of(
                new NationalHolidayProviderRow(LocalDate.of(2026, 1, 1), "신정"),
                new NationalHolidayProviderRow(LocalDate.of(2026, 5, 5), "어린이날")
        );

        // when
        nationalHolidayCommandService.insertIfMissing(missingProviderRows);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NationalHoliday>> holidays = ArgumentCaptor.forClass(List.class);
        verify(nationalHolidayRepository).saveAllAndFlush(holidays.capture());
        assertThat(holidays.getValue())
                .extracting(NationalHoliday::getHolidayDate, NationalHoliday::getHolidayTitle)
                .containsExactly(
                        tuple(LocalDate.of(2026, 1, 1), "신정"),
                        tuple(LocalDate.of(2026, 5, 5), "어린이날")
                );
    }

    @Test
    @DisplayName("stale 공휴일 목록은 추가 판단 없이 그대로 삭제한다")
    void givenStaleHolidays_whenDeleteStaleHolidays_thenOnlyDeletesHolidays() {
        // given
        List<NationalHoliday> staleHolidays = List.of(
                new NationalHoliday(LocalDate.of(2026, 5, 5), "어린이날")
        );

        // when
        nationalHolidayCommandService.deleteStaleHolidays(staleHolidays);

        // then
        verify(nationalHolidayRepository).deleteAll(staleHolidays);
    }
}
