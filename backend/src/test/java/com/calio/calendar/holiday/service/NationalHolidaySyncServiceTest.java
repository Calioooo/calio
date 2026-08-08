package com.calio.calendar.holiday.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.holiday.client.HolidayApiClient;
import com.calio.calendar.holiday.client.HolidayApiProperties;
import com.calio.calendar.holiday.client.dto.HolidayApiItem;
import com.calio.calendar.holiday.client.dto.HolidayApiResponse;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import com.calio.calendar.holiday.domain.NationalHoliday;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import tools.jackson.databind.ObjectMapper;

class NationalHolidaySyncServiceTest {

    private FakeHolidayApiClient holidayApiClient;
    private FakeNationalHolidayRepository nationalHolidayRepository;
    private NationalHolidaySyncService nationalHolidaySyncService;

    @BeforeEach
    void setUp() {
        holidayApiClient = new FakeHolidayApiClient();
        nationalHolidayRepository = new FakeNationalHolidayRepository();
        nationalHolidaySyncService = new NationalHolidaySyncService(
                holidayApiClient,
                new NationalHolidayPersistenceService(
                        nationalHolidayRepository.proxy(),
                        new NoOpTransactionManager()
                )
        );
    }

    @Test
    @DisplayName("성공한 year sync는 isHoliday가 Y인 item만 date/title holiday row로 저장한다")
    void givenSuccessfulResponse_whenSyncYear_thenStoresOnlyHolidayItems() {
        // given
        holidayApiClient.addResponse(2026, new HolidayApiResponse(
                "00",
                List.of(
                        new HolidayApiItem("20260101", "신정", "Y"),
                        new HolidayApiItem("20260214", "기념일", "N")
                )
        ));

        // when
        nationalHolidaySyncService.syncYear(2026);

        // then
        assertThat(nationalHolidayRepository.savedHolidays()).singleElement()
                .satisfies(holiday -> {
                    assertThat(holiday.getHolidayDate()).isEqualTo(LocalDate.parse("2026-01-01"));
                    assertThat(holiday.getHolidayTitle()).isEqualTo("신정");
                });
        assertThat(nationalHolidayRepository.existsChecks())
                .containsExactly(new HolidayRow(LocalDate.parse("2026-01-01"), "신정"));
    }

    @Test
    @DisplayName("성공한 year sync는 provider snapshot에 없는 같은 연도 기존 row를 삭제한다")
    void givenSuccessfulResponseWithMissingExistingRow_whenSyncYear_thenDeletesSameYearStaleRows() {
        // given
        NationalHoliday staleHoliday = new NationalHoliday(LocalDate.parse("2026-05-05"), "어린이날");
        NationalHoliday retainedHoliday = new NationalHoliday(LocalDate.parse("2026-10-03"), "개천절");
        nationalHolidayRepository.addExisting(staleHoliday, retainedHoliday);
        holidayApiClient.addResponse(2026, new HolidayApiResponse(
                "00",
                List.of(new HolidayApiItem("20261003", "개천절", "Y"))
        ));

        // when
        nationalHolidaySyncService.syncYear(2026);

        // then
        assertThat(nationalHolidayRepository.deletedHolidays()).containsExactly(staleHoliday);
        assertThat(nationalHolidayRepository.savedHolidays()).isEmpty();
    }

    @Test
    @DisplayName("non-00 resultCode 응답은 기존 DB row를 삭제하지 않는다")
    void givenFailedResultCode_whenSyncYear_thenPreservesExistingRows() {
        // given
        holidayApiClient.addResponse(2026, new HolidayApiResponse("99", List.of()));

        // when
        nationalHolidaySyncService.syncYear(2026);

        // then
        assertThat(nationalHolidayRepository.findRangeCalled()).isFalse();
        assertThat(nationalHolidayRepository.deletedHolidays()).isEmpty();
    }

    @Test
    @DisplayName("한 year의 외부 API 장애는 EXTERNAL_API_UNAVAILABLE 예외로 처리되어 다음 year sync를 막지 않는다")
    void givenOneYearExternalApiFailure_whenSyncYearRange_thenContinuesNextYear() {
        // given
        holidayApiClient.addFailure(
                2026,
                new CalioException(ErrorCode.EXTERNAL_API_UNAVAILABLE, new IllegalStateException("api failure"))
        );
        holidayApiClient.addResponse(2027, new HolidayApiResponse(
                "00",
                List.of(new HolidayApiItem("20270101", "신정", "Y"))
        ));

        // when
        nationalHolidaySyncService.syncYearRange(2026, 2027);

        // then
        assertThat(holidayApiClient.requestedYears()).containsExactly(2026, 2027);
        assertThat(nationalHolidayRepository.findRangeCalled()).isTrue();
    }

    @Test
    @DisplayName("성공 응답이지만 items가 비어 있는 경우, 기존 공휴일을 삭제하지 않는다.")
    void givenEmptyProviderRows_whenSyncYear_thenPreservesExistingRows() {
        // given
        nationalHolidayRepository.addExisting(new NationalHoliday(LocalDate.parse("2026-01-01"), "신정"));
        holidayApiClient.addResponse(2026, new HolidayApiResponse("00", List.of()));

        // when
        nationalHolidaySyncService.syncYear(2026);

        // then
        assertThat(nationalHolidayRepository.findRangeCalled()).isFalse();
        assertThat(nationalHolidayRepository.deletedHolidays()).isEmpty();
        assertThat(nationalHolidayRepository.savedHolidays()).isEmpty();
    }

    @Test
    @DisplayName("중복 insert 경합은 DuplicateKeyException을 이미 존재하는 row로 보고 sync를 계속한다")
    void givenDuplicateInsertRace_whenSyncYear_thenContinuesSnapshotSync() {
        // given
        NationalHoliday staleHoliday = new NationalHoliday(LocalDate.parse("2026-12-25"), "성탄절");
        nationalHolidayRepository.addExisting(staleHoliday);
        nationalHolidayRepository.throwDuplicateOnSave();
        holidayApiClient.addResponse(2026, new HolidayApiResponse(
                "00",
                List.of(new HolidayApiItem("20260101", "신정", "Y"))
        ));

        // when
        nationalHolidaySyncService.syncYear(2026);

        // then
        assertThat(nationalHolidayRepository.deletedHolidays()).containsExactly(staleHoliday);
    }

    @Test
    @DisplayName("provider localDate parsing 실패는 snapshot 실패로 보고 기존 DB row를 보존한다")
    void givenInvalidProviderLocalDate_whenSyncYear_thenPreservesExistingRows() {
        // given
        holidayApiClient.addResponse(2026, new HolidayApiResponse(
                "00",
                List.of(new HolidayApiItem("invalid", "신정", "Y"))
        ));

        // when
        nationalHolidaySyncService.syncYear(2026);

        // then
        assertThat(nationalHolidayRepository.findRangeCalled()).isFalse();
        assertThat(nationalHolidayRepository.deletedHolidays()).isEmpty();
    }

    @Test
    @DisplayName("이미 존재하는 date/title 조합은 새 row로 저장하지 않는다")
    void givenExistingDateTitleCombination_whenSyncYear_thenSkipsInsert() {
        // given
        nationalHolidayRepository.addExisting(new NationalHoliday(LocalDate.parse("2026-01-01"), "신정"));
        holidayApiClient.addResponse(2026, new HolidayApiResponse(
                "00",
                List.of(new HolidayApiItem("20260101", "신정", "Y"))
        ));

        // when
        nationalHolidaySyncService.syncYear(2026);

        // then
        assertThat(nationalHolidayRepository.savedHolidays()).isEmpty();
        assertThat(nationalHolidayRepository.deletedHolidays()).isEmpty();
    }

    private static class FakeHolidayApiClient extends HolidayApiClient {

        private final List<Integer> requestedYears = new ArrayList<>();
        private final List<YearResponse> responses = new ArrayList<>();
        private final List<YearFailure> failures = new ArrayList<>();

        private FakeHolidayApiClient() {
            super(new HolidayApiProperties(), new ObjectMapper());
        }

        @Override
        public HolidayApiResponse fetchHolidays(int year) {
            requestedYears.add(year);
            for (YearFailure failure : failures) {
                if (failure.year() == year) {
                    throw failure.exception();
                }
            }
            return responses.stream()
                    .filter(response -> response.year() == year)
                    .findFirst()
                    .map(YearResponse::response)
                    .orElseThrow(() -> new IllegalStateException("missing fake response"));
        }

        private void addResponse(int year, HolidayApiResponse response) {
            responses.add(new YearResponse(year, response));
        }

        private void addFailure(int year, RuntimeException exception) {
            failures.add(new YearFailure(year, exception));
        }

        private List<Integer> requestedYears() {
            return requestedYears;
        }

        private record YearResponse(int year, HolidayApiResponse response) {
        }

        private record YearFailure(int year, RuntimeException exception) {
        }
    }

    private static class FakeNationalHolidayRepository {

        private final List<NationalHoliday> existingHolidays = new ArrayList<>();
        private final List<NationalHoliday> savedHolidays = new ArrayList<>();
        private final List<NationalHoliday> deletedHolidays = new ArrayList<>();
        private final List<HolidayRow> existsChecks = new ArrayList<>();
        private boolean throwDuplicateOnSave;
        private boolean findRangeCalled;

        private NationalHolidayRepository proxy() {
            return (NationalHolidayRepository) Proxy.newProxyInstance(
                    NationalHolidayRepository.class.getClassLoader(),
                    new Class<?>[]{NationalHolidayRepository.class},
                    (proxy, method, args) -> invoke(method.getName(), args)
            );
        }

        private Object invoke(String methodName, Object[] args) {
            return switch (methodName) {
                case "findByHolidayDateBetween" -> findByHolidayDateBetween((LocalDate) args[0], (LocalDate) args[1]);
                case "existsByHolidayDateAndHolidayTitle" ->
                        existsByHolidayDateAndHolidayTitle((LocalDate) args[0], (String) args[1]);
                case "saveAndFlush" -> saveAndFlush((NationalHoliday) args[0]);
                case "deleteAll" -> deleteAll(args[0]);
                case "toString" -> "FakeNationalHolidayRepository";
                default -> throw new UnsupportedOperationException(methodName);
            };
        }

        private List<NationalHoliday> findByHolidayDateBetween(LocalDate from, LocalDate to) {
            findRangeCalled = true;
            return existingHolidays.stream()
                    .filter(holiday -> !holiday.getHolidayDate().isBefore(from))
                    .filter(holiday -> !holiday.getHolidayDate().isAfter(to))
                    .toList();
        }

        private boolean existsByHolidayDateAndHolidayTitle(LocalDate holidayDate, String holidayTitle) {
            existsChecks.add(new HolidayRow(holidayDate, holidayTitle));
            return existingHolidays.stream()
                    .anyMatch(holiday -> holiday.getHolidayDate().equals(holidayDate)
                            && holiday.getHolidayTitle().equals(holidayTitle));
        }

        private NationalHoliday saveAndFlush(NationalHoliday nationalHoliday) {
            if (throwDuplicateOnSave) {
                throw new DuplicateKeyException("duplicate");
            }
            savedHolidays.add(nationalHoliday);
            return nationalHoliday;
        }

        private Object deleteAll(Object holidays) {
            deletedHolidays.addAll((List<NationalHoliday>) holidays);
            return null;
        }

        private void addExisting(NationalHoliday... holidays) {
            existingHolidays.addAll(List.of(holidays));
        }

        private void throwDuplicateOnSave() {
            throwDuplicateOnSave = true;
        }

        private List<NationalHoliday> savedHolidays() {
            return savedHolidays;
        }

        private List<NationalHoliday> deletedHolidays() {
            return deletedHolidays;
        }

        private List<HolidayRow> existsChecks() {
            return existsChecks;
        }

        private boolean findRangeCalled() {
            return findRangeCalled;
        }
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

    private record HolidayRow(LocalDate holidayDate, String holidayTitle) {
    }
}
