package com.calio.calendar.holiday.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class NationalHolidayCommandServiceTest {

    private NationalHolidayRepository nationalHolidayRepository;
    private NationalHolidayCommandService nationalHolidayCommandService;

    @BeforeEach
    void setUp() {
        nationalHolidayRepository = mock(NationalHolidayRepository.class);
        nationalHolidayCommandService = new NationalHolidayCommandService(
                nationalHolidayRepository,
                new NoOpTransactionManager()
        );
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
    @DisplayName("insert 중 DataIntegrityViolationException이 발생해도 중복 경합으로 처리하고 snapshot 반영을 계속한다")
    void givenIntegrityConflict_whenApplySnapshot_thenContinuesReconciliation() {
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
        assertThatCode(() -> nationalHolidayCommandService.applySnapshot(
                2026,
                Set.of(new NationalHolidayProviderRow(holidayDate, "신정"))
        )).doesNotThrowAnyException();
        verify(nationalHolidayRepository).deleteAll(List.of(staleHoliday));
    }

    private static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() throws TransactionException {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
        }
    }
}
