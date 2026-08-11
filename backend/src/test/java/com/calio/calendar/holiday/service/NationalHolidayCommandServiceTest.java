package com.calio.calendar.holiday.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.calio.calendar.holiday.domain.NationalHoliday;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

class NationalHolidayCommandServiceTest {

    private NationalHolidayRepository nationalHolidayRepository;
    private NationalHolidayCommandService nationalHolidayCommandService;

    @BeforeEach
    void setUp() {
        nationalHolidayRepository = mock(NationalHolidayRepository.class);
        nationalHolidayCommandService = new NationalHolidayCommandService(nationalHolidayRepository);
    }

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
    @DisplayName("provider snapshot은 새 공휴일을 추가하고 유지된 공휴일은 보존하며 누락된 공휴일은 삭제한다")
    void givenProviderSnapshot_whenApplySnapshot_thenReconcilesSameYearHolidays() {
        // given
        LocalDate newYearsDay = LocalDate.of(2026, 1, 1);
        LocalDate childrensDay = LocalDate.of(2026, 5, 5);
        LocalDate memorialDay = LocalDate.of(2026, 6, 6);
        NationalHoliday staleHoliday = new NationalHoliday(childrensDay, "어린이날");
        NationalHoliday retainedHoliday = new NationalHoliday(memorialDay, "현충일");
        given(nationalHolidayRepository.findByHolidayDateBetween(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        )).willReturn(List.of(staleHoliday, retainedHoliday));
        given(nationalHolidayRepository.existsByHolidayDateAndHolidayTitle(newYearsDay, "신정"))
                .willReturn(false);
        given(nationalHolidayRepository.existsByHolidayDateAndHolidayTitle(memorialDay, "현충일"))
                .willReturn(true);

        // when
        nationalHolidayCommandService.applySnapshot(2026, Set.of(
                new NationalHolidayProviderRow(newYearsDay, "신정"),
                new NationalHolidayProviderRow(memorialDay, "현충일")
        ));

        // then
        ArgumentCaptor<NationalHoliday> savedHoliday = ArgumentCaptor.forClass(NationalHoliday.class);
        verify(nationalHolidayRepository).saveAndFlush(savedHoliday.capture());
        assertThat(savedHoliday.getValue().getHolidayDate()).isEqualTo(newYearsDay);
        assertThat(savedHoliday.getValue().getHolidayTitle()).isEqualTo("신정");
        verify(nationalHolidayRepository).deleteAll(List.of(staleHoliday));
    }

    @Test
    @DisplayName("공휴일 추가가 실패하면 stale 삭제를 진행하지 않고 예외를 전파한다")
    void givenIntegrityConflict_whenApplySnapshot_thenStopsBeforeDeletion() {
        // given
        LocalDate holidayDate = LocalDate.of(2026, 1, 1);
        NationalHoliday staleHoliday = new NationalHoliday(LocalDate.of(2026, 5, 5), "어린이날");
        given(nationalHolidayRepository.findByHolidayDateBetween(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        )).willReturn(List.of(staleHoliday));
        given(nationalHolidayRepository.existsByHolidayDateAndHolidayTitle(holidayDate, "신정"))
                .willReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate race"))
                .when(nationalHolidayRepository)
                .saveAndFlush(any(NationalHoliday.class));

        // when, then
        assertThatThrownBy(() -> nationalHolidayCommandService.applySnapshot(
                2026,
                Set.of(new NationalHolidayProviderRow(holidayDate, "신정"))
        )).isInstanceOf(DataIntegrityViolationException.class);
        verify(nationalHolidayRepository, never()).deleteAll(List.of(staleHoliday));
    }
}
