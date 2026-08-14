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
    @DisplayName("snapshot 교체는 누락 공휴일을 저장하고 stale 공휴일을 같은 Command 안에서 삭제한다")
    void givenSnapshotChanges_whenReplaceSnapshot_thenWritesBothChanges() {
        // given
        List<NationalHolidayProviderRow> missingProviderRows = List.of(
                new NationalHolidayProviderRow(LocalDate.of(2026, 1, 1), "신정"),
                new NationalHolidayProviderRow(LocalDate.of(2026, 5, 5), "어린이날")
        );

        List<NationalHoliday> staleHolidays = List.of(
                new NationalHoliday(LocalDate.of(2026, 5, 5), "어린이날")
        );

        // when
        nationalHolidayCommandService.replaceSnapshot(missingProviderRows, staleHolidays);

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
        verify(nationalHolidayRepository).deleteAll(staleHolidays);
    }
}
