package com.calio.calendar.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.client.HolidayApiClient;
import com.calio.calendar.client.dto.HolidayApiResponse;
import com.calio.calendar.config.HolidayApiProperties;
import com.calio.calendar.repository.NationalHolidayRepository;
import com.calio.calendar.service.NationalHolidayPersistenceService;
import com.calio.calendar.service.NationalHolidaySyncService;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import tools.jackson.databind.ObjectMapper;

class NationalHolidaySyncSchedulerTest {

    @Test
    @DisplayName("monthly full sync는 현재 연도 기준 -20년부터 +20년까지 요청한다")
    void givenServiceKey_whenRunMonthlyFullSync_thenSyncsFullYearRange() {
        // given
        FakeNationalHolidaySyncService syncService = new FakeNationalHolidaySyncService();
        NationalHolidaySyncScheduler scheduler = scheduler(syncService, true, "2026-06-01T19:00:00Z");

        // when
        scheduler.syncMonthlyFullRange();

        // then
        assertThat(syncService.requests()).containsExactly(new YearRange(2006, 2046));
    }

    @Test
    @DisplayName("daily near sync는 현재 연도부터 +2년까지 요청한다")
    void givenServiceKeyAndNonMonthlyDay_whenRunDailyNearSync_thenSyncsNearYearRange() {
        // given
        FakeNationalHolidaySyncService syncService = new FakeNationalHolidaySyncService();
        NationalHolidaySyncScheduler scheduler = scheduler(syncService, true, "2026-06-02T19:00:00Z");

        // when
        scheduler.syncDailyNearRange();

        // then
        assertThat(syncService.requests()).containsExactly(new YearRange(2026, 2028));
    }

    @Test
    @DisplayName("매월 1일 daily near sync는 monthly full sync와 겹치지 않도록 skip한다")
    void givenFirstDayOfMonth_whenRunDailyNearSync_thenSkipsOverlap() {
        // given
        FakeNationalHolidaySyncService syncService = new FakeNationalHolidaySyncService();
        NationalHolidaySyncScheduler scheduler = scheduler(syncService, true, "2026-06-30T19:00:00Z");

        // when
        scheduler.syncDailyNearRange();

        // then
        assertThat(syncService.requests()).isEmpty();
    }

    @Test
    @DisplayName("service key가 없으면 scheduler job은 외부 sync service를 호출하지 않는다")
    void givenMissingServiceKey_whenRunScheduledSync_thenSkipsExternalSync() {
        // given
        FakeNationalHolidaySyncService syncService = new FakeNationalHolidaySyncService();
        NationalHolidaySyncScheduler scheduler = scheduler(syncService, false, "2026-06-02T19:00:00Z");

        // when
        scheduler.syncMonthlyFullRange();
        scheduler.syncDailyNearRange();

        // then
        assertThat(syncService.requests()).isEmpty();
    }

    @Test
    @DisplayName("sync 실행 중 다른 scheduler job이 시작되면 중복 실행을 skip한다.")
    void givenSyncAlreadyRunning_whenAnotherSchedulerJobStarts_thenSkipsDuplicateRun() {
        // given
        FakeNationalHolidaySyncService syncService = new FakeNationalHolidaySyncService();
        NationalHolidaySyncScheduler scheduler = scheduler(syncService, true, "2026-06-02T19:00:00Z");
        syncService.runOnceDuringSync(scheduler::syncDailyNearRange);

        // when
        scheduler.syncMonthlyFullRange();

        // then
        assertThat(syncService.requests()).containsExactly(new YearRange(2006, 2046));
    }

    private NationalHolidaySyncScheduler scheduler(
            FakeNationalHolidaySyncService syncService,
            boolean hasServiceKey,
            String instant
    ) {
        return new NationalHolidaySyncScheduler(
                syncService,
                new FakeHolidayApiProperties(hasServiceKey),
                Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul"))
        );
    }

    private static class FakeNationalHolidaySyncService extends NationalHolidaySyncService {

        private final List<YearRange> requests = new ArrayList<>();
        private Runnable onSync;

        private FakeNationalHolidaySyncService() {
            super(
                    new FakeHolidayApiClient(),
                    new NationalHolidayPersistenceService(
                            fakeRepository(),
                            new NoOpTransactionManager()
                    )
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

    private static class FakeHolidayApiProperties extends HolidayApiProperties {

        private final boolean hasServiceKey;

        private FakeHolidayApiProperties(boolean hasServiceKey) {
            this.hasServiceKey = hasServiceKey;
        }

        @Override
        public boolean hasServiceKey() {
            return hasServiceKey;
        }
    }

    private static class FakeHolidayApiClient extends HolidayApiClient {

        private FakeHolidayApiClient() {
            super(new HolidayApiProperties(), new ObjectMapper());
        }

        @Override
        public HolidayApiResponse fetchHolidays(int year) {
            throw new UnsupportedOperationException("scheduler test should not fetch holidays");
        }
    }

    private static NationalHolidayRepository fakeRepository() {
        return (NationalHolidayRepository) Proxy.newProxyInstance(
                NationalHolidayRepository.class.getClassLoader(),
                new Class<?>[]{NationalHolidayRepository.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                }
        );
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

    private record YearRange(int startYear, int endYear) {
    }
}
