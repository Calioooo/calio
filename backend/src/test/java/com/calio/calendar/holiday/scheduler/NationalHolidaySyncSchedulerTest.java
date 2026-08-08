package com.calio.calendar.holiday.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.calio.calendar.holiday.client.HolidayApiProperties;
import com.calio.calendar.holiday.service.NationalHolidayCommandService;
import com.calio.calendar.holiday.service.NationalHolidayQueryService;
import com.calio.calendar.holiday.service.NationalHolidayService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NationalHolidaySyncSchedulerTest {

    @Test
    @DisplayName("monthly full sync는 현재 연도 기준 -20년부터 +20년까지 요청한다")
    void givenServiceKey_whenRunMonthlyFullSync_thenSyncsFullYearRange() {
        // given
        FakeNationalHolidayService holidayService = new FakeNationalHolidayService();
        NationalHolidaySyncScheduler scheduler = scheduler(holidayService, true, "2026-06-01T19:00:00Z");

        // when
        scheduler.syncMonthlyFullRange();

        // then
        assertThat(holidayService.requests()).containsExactly(new YearRange(2006, 2046));
    }

    @Test
    @DisplayName("daily near sync는 현재 연도부터 +2년까지 요청한다")
    void givenServiceKeyAndNonMonthlyDay_whenRunDailyNearSync_thenSyncsNearYearRange() {
        // given
        FakeNationalHolidayService holidayService = new FakeNationalHolidayService();
        NationalHolidaySyncScheduler scheduler = scheduler(holidayService, true, "2026-06-02T19:00:00Z");

        // when
        scheduler.syncDailyNearRange();

        // then
        assertThat(holidayService.requests()).containsExactly(new YearRange(2026, 2028));
    }

    @Test
    @DisplayName("매월 1일 daily near sync는 monthly full sync와 겹치지 않도록 skip한다")
    void givenFirstDayOfMonth_whenRunDailyNearSync_thenSkipsOverlap() {
        // given
        FakeNationalHolidayService holidayService = new FakeNationalHolidayService();
        NationalHolidaySyncScheduler scheduler = scheduler(holidayService, true, "2026-06-30T19:00:00Z");

        // when
        scheduler.syncDailyNearRange();

        // then
        assertThat(holidayService.requests()).isEmpty();
    }

    @Test
    @DisplayName("service key가 없으면 scheduler job은 외부 sync service를 호출하지 않는다")
    void givenMissingServiceKey_whenRunScheduledSync_thenSkipsExternalSync() {
        // given
        FakeNationalHolidayService holidayService = new FakeNationalHolidayService();
        NationalHolidaySyncScheduler scheduler = scheduler(holidayService, false, "2026-06-02T19:00:00Z");

        // when
        scheduler.syncMonthlyFullRange();
        scheduler.syncDailyNearRange();

        // then
        assertThat(holidayService.requests()).isEmpty();
    }

    @Test
    @DisplayName("sync 실행 중 다른 scheduler job이 시작되면 중복 실행을 skip한다.")
    void givenSyncAlreadyRunning_whenAnotherSchedulerJobStarts_thenSkipsDuplicateRun() {
        // given
        FakeNationalHolidayService holidayService = new FakeNationalHolidayService();
        NationalHolidaySyncScheduler scheduler = scheduler(holidayService, true, "2026-06-02T19:00:00Z");
        holidayService.runOnceDuringSync(scheduler::syncDailyNearRange);

        // when
        scheduler.syncMonthlyFullRange();

        // then
        assertThat(holidayService.requests()).containsExactly(new YearRange(2006, 2046));
    }

    private NationalHolidaySyncScheduler scheduler(
            FakeNationalHolidayService holidayService,
            boolean hasServiceKey,
            String instant
    ) {
        return new NationalHolidaySyncScheduler(
                holidayService,
                holidayApiProperties(hasServiceKey),
                Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"))
        );
    }

    private HolidayApiProperties holidayApiProperties(boolean hasServiceKey) {
        HolidayApiProperties properties = new HolidayApiProperties();
        properties.setServiceKey(hasServiceKey ? "test-service-key" : "");
        return properties;
    }

    private static class FakeNationalHolidayService extends NationalHolidayService {

        private final List<YearRange> requests = new ArrayList<>();
        private Runnable onSync;

        private FakeNationalHolidayService() {
            super(
                    mock(com.calio.calendar.holiday.client.HolidayApiClient.class),
                    mock(NationalHolidayQueryService.class),
                    mock(NationalHolidayCommandService.class)
            );
        }

        @Override
        public void syncYearRange(int startYear, int endYear) {
            requests.add(new YearRange(startYear, endYear));
            if (onSync == null) {
                return;
            }

            Runnable callback = onSync;
            onSync = null;
            callback.run();
        }

        private List<YearRange> requests() {
            return requests;
        }

        private void runOnceDuringSync(Runnable callback) {
            onSync = callback;
        }
    }

    private record YearRange(int startYear, int endYear) {
    }
}
