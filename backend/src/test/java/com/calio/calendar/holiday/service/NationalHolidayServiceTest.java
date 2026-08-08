package com.calio.calendar.holiday.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.holiday.client.HolidayApiClient;
import com.calio.calendar.holiday.client.dto.HolidayApiItem;
import com.calio.calendar.holiday.client.dto.HolidayApiResponse;
import com.calio.calendar.holiday.controller.dto.NationalHolidayResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NationalHolidayServiceTest {

    @Mock
    private HolidayApiClient holidayApiClient;

    @Mock
    private NationalHolidayQueryService nationalHolidayQueryService;

    @Mock
    private NationalHolidayCommandService nationalHolidayCommandService;

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

    @Test
    @DisplayName("성공한 연도 동기화는 공휴일만 중복 없이 정규화해 CommandService에 전달한다")
    void givenSuccessfulProviderResponse_whenSyncYear_thenDelegatesCanonicalSnapshot() {
        // given
        given(holidayApiClient.fetchHolidays(2026)).willReturn(new HolidayApiResponse(
                "00",
                List.of(
                        new HolidayApiItem("20260101", "신정", "Y"),
                        new HolidayApiItem("20260101", "신정", "Y"),
                        new HolidayApiItem("20260214", "기념일", "N")
                )
        ));

        // when
        nationalHolidayService.syncYear(2026);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<NationalHolidayProviderRow>> providerRows = ArgumentCaptor.forClass(Set.class);
        verify(nationalHolidayCommandService).applySnapshot(eq(2026), providerRows.capture());
        assertThat(providerRows.getValue()).containsExactly(
                new NationalHolidayProviderRow(LocalDate.of(2026, 1, 1), "신정")
        );
    }

    @Test
    @DisplayName("provider 실패 resultCode는 기존 snapshot을 변경하지 않는다")
    void givenFailedProviderResultCode_whenSyncYear_thenSkipsCommand() {
        // given
        given(holidayApiClient.fetchHolidays(2026))
                .willReturn(new HolidayApiResponse("99", List.of()));

        // when
        nationalHolidayService.syncYear(2026);

        // then
        verifyNoInteractions(nationalHolidayCommandService);
    }

    @Test
    @DisplayName("성공 응답에 공휴일이 없으면 기존 snapshot을 삭제하지 않는다")
    void givenResponseWithoutHolidayItems_whenSyncYear_thenSkipsCommand() {
        // given
        given(holidayApiClient.fetchHolidays(2026)).willReturn(new HolidayApiResponse(
                "00",
                List.of(new HolidayApiItem("20260214", "기념일", "N"))
        ));

        // when
        nationalHolidayService.syncYear(2026);

        // then
        verifyNoInteractions(nationalHolidayCommandService);
    }

    @Test
    @DisplayName("한 연도의 외부 API 장애는 다음 연도 동기화를 막지 않는다")
    void givenOneYearApiFailure_whenSyncYearRange_thenContinuesNextYear() {
        // given
        given(holidayApiClient.fetchHolidays(2026))
                .willThrow(new CalioException(ErrorCode.EXTERNAL_API_UNAVAILABLE));
        given(holidayApiClient.fetchHolidays(2027)).willReturn(new HolidayApiResponse(
                "00",
                List.of(new HolidayApiItem("20270101", "신정", "Y"))
        ));

        // when
        nationalHolidayService.syncYearRange(2026, 2027);

        // then
        InOrder requests = inOrder(holidayApiClient);
        requests.verify(holidayApiClient).fetchHolidays(2026);
        requests.verify(holidayApiClient).fetchHolidays(2027);
        verify(nationalHolidayCommandService, never()).applySnapshot(eq(2026), anySet());
        verify(nationalHolidayCommandService).applySnapshot(
                2027,
                Set.of(new NationalHolidayProviderRow(LocalDate.of(2027, 1, 1), "신정"))
        );
    }

    @Test
    @DisplayName("잘못된 locdate는 provider snapshot 전체를 적용하지 않고 기존 데이터를 보존한다")
    void givenInvalidLocdate_whenSyncYear_thenSkipsCommand() {
        // given
        given(holidayApiClient.fetchHolidays(2026)).willReturn(new HolidayApiResponse(
                "00",
                List.of(new HolidayApiItem("invalid", "신정", "Y"))
        ));

        // when
        nationalHolidayService.syncYear(2026);

        // then
        verifyNoInteractions(nationalHolidayCommandService);
    }
}
