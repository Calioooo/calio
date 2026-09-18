package com.calio.calendar.holiday.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.holiday.domain.NationalHoliday;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import com.calio.calendar.holiday.usecase.dto.NationalHolidayContent;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class NationalHolidayUseCaseTest {

  @Mock private HolidayApiClient holidayApiClient;

  @Mock private NationalHolidayRepository nationalHolidayRepository;

  @Mock private TransactionTemplate transactionTemplate;

  private GetNationalHolidaysUseCase getNationalHolidaysUseCase;
  private SyncNationalHolidaysUseCase syncNationalHolidaysUseCase;

  @BeforeEach
  void setUp() {
    getNationalHolidaysUseCase = new GetNationalHolidaysUseCase(nationalHolidayRepository);
    syncNationalHolidaysUseCase =
        new SyncNationalHolidaysUseCase(
            holidayApiClient, nationalHolidayRepository, transactionTemplate);
    lenient()
        .doAnswer(
            invocation -> {
              invocation.getArgument(0, java.util.function.Consumer.class).accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
  }

  @Nested
  @DisplayName("공휴일 조회")
  class GetNationalHolidays {

    @Test
    @DisplayName("날짜와 제목 순서의 repository 결과를 응답으로 변환한다")
    void givenValidRange_whenGet_thenReturnsOrderedResponses() {
      LocalDate from = LocalDate.of(2026, 5, 5);
      LocalDate to = LocalDate.of(2026, 6, 6);
      when(nationalHolidayRepository.findAllInDateRange(from, to))
          .thenReturn(List.of(new NationalHoliday(from, "어린이날")));

      var result = getNationalHolidaysUseCase.get(from, to);

      assertThat(result)
          .singleElement()
          .satisfies(
              holiday -> {
                assertThat(holiday.holidayDate()).isEqualTo(from);
                assertThat(holiday.holidayTitle()).isEqualTo("어린이날");
              });
      verify(nationalHolidayRepository).findAllInDateRange(from, to);
    }

    @Test
    @DisplayName("시작일이 종료일보다 늦으면 INVALID_TIME_RANGE를 반환한다")
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

  @Nested
  @DisplayName("공휴일 동기화")
  class SyncNationalHolidays {

    @Test
    @DisplayName("새 공휴일을 저장하고 더 이상 없는 공휴일을 삭제한다")
    void givenSuccessfulProviderResponse_whenSync_thenAppliesHolidayChangesInsideTransaction() {
      NationalHoliday stale = new NationalHoliday(LocalDate.of(2026, 5, 5), "어린이날");
      when(holidayApiClient.fetchHolidays(2026))
          .thenReturn(
              List.of(
                  new NationalHolidayContent(LocalDate.of(2026, 1, 1), "신정"),
                  new NationalHolidayContent(LocalDate.of(2026, 6, 6), "현충일")));
      when(nationalHolidayRepository.findByHolidayDateBetween(
              LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
          .thenReturn(List.of(stale));

      syncNationalHolidaysUseCase.syncYearRange(2026, 2026);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<NationalHoliday>> saved = ArgumentCaptor.forClass(List.class);
      verify(nationalHolidayRepository).saveAllAndFlush(saved.capture());
      assertThat(saved.getValue())
          .extracting(NationalHoliday::getHolidayTitle)
          .containsExactlyInAnyOrder("신정", "현충일");
      verify(nationalHolidayRepository).deleteAll(List.of(stale));
    }

    @Test
    @DisplayName("공휴일 내용이 없으면 기존 공휴일을 변경하지 않는다")
    void givenEmptyFetchedHolidays_whenSync_thenSkipsHolidayChanges() {
      when(holidayApiClient.fetchHolidays(2026)).thenReturn(List.of());
      when(holidayApiClient.fetchHolidays(2027)).thenReturn(List.of());

      syncNationalHolidaysUseCase.syncYearRange(2026, 2027);

      verify(nationalHolidayRepository, never()).saveAllAndFlush(any());
      verifyNoInteractions(nationalHolidayRepository);
    }
  }
}
