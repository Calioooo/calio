package com.calio.calendar.holiday.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.calio.calendar.holiday.client.HolidayApiClient;
import com.calio.calendar.holiday.client.dto.HolidayApiItem;
import com.calio.calendar.holiday.client.dto.HolidayApiResponse;
import com.calio.calendar.holiday.domain.NationalHoliday;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class SyncNationalHolidaysUseCaseTest {

  @Mock private HolidayApiClient holidayApiClient;

  @Mock private NationalHolidayRepository nationalHolidayRepository;

  @Mock private TransactionTemplate transactionTemplate;

  private SyncNationalHolidaysUseCase syncNationalHolidaysUseCase;

  @BeforeEach
  void setUp() {
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

  @Test
  @DisplayName("성공한 동기화는 외부 응답과 기존 snapshot의 차이만 저장하고 stale 공휴일을 삭제한다")
  void givenSuccessfulProviderResponse_whenSync_thenReplacesSnapshotInsideTransaction() {
    NationalHoliday stale = new NationalHoliday(LocalDate.of(2026, 5, 5), "어린이날");
    when(holidayApiClient.fetchHolidays(2026))
        .thenReturn(
            new HolidayApiResponse(
                "00",
                List.of(
                    new HolidayApiItem("20260101", "신정", "Y"),
                    new HolidayApiItem("20260606", "현충일", "Y"))));
    when(nationalHolidayRepository.findByHolidayDateBetween(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        .thenReturn(List.of(stale));

    syncNationalHolidaysUseCase.syncYearRange(2026, 2026);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<NationalHoliday>> saved = ArgumentCaptor.forClass(List.class);
    verify(nationalHolidayRepository).saveAllAndFlush(saved.capture());
    assertThat(saved.getValue())
        .extracting(NationalHoliday::getHolidayTitle)
        .containsExactly("신정", "현충일");
    verify(nationalHolidayRepository).deleteAll(List.of(stale));
  }

  @Test
  @DisplayName("실패 응답 또는 공휴일 없는 성공 응답은 기존 snapshot을 변경하지 않는다")
  void givenNonApplicableProviderResponse_whenSync_thenSkipsSnapshot() {
    when(holidayApiClient.fetchHolidays(2026)).thenReturn(new HolidayApiResponse("99", List.of()));
    when(holidayApiClient.fetchHolidays(2027))
        .thenReturn(
            new HolidayApiResponse("00", List.of(new HolidayApiItem("20270214", "기념일", "N"))));

    syncNationalHolidaysUseCase.syncYearRange(2026, 2027);

    verify(nationalHolidayRepository, never()).saveAllAndFlush(any());
    verifyNoInteractions(nationalHolidayRepository);
  }
}
