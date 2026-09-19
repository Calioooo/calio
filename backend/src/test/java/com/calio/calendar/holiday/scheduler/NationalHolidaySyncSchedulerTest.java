package com.calio.calendar.holiday.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.calio.calendar.holiday.client.HolidayApiProperties;
import com.calio.calendar.holiday.usecase.SyncNationalHolidaysUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NationalHolidaySyncSchedulerTest {

  @Mock private SyncNationalHolidaysUseCase syncNationalHolidaysUseCase;

  @Test
  @DisplayName("monthly full sync는 현재 연도 기준 -20년부터 +20년까지 동기화한다")
  void givenServiceKey_whenRunMonthlyFullSync_thenSyncsFullYearRange() {
    NationalHolidaySyncScheduler scheduler = scheduler(true, "2026-06-01T19:00:00Z");

    scheduler.syncMonthlyFullRange();

    verify(syncNationalHolidaysUseCase).syncYearRange(2006, 2046);
  }

  @Test
  @DisplayName("daily near sync는 현재 연도부터 +2년까지 동기화한다")
  void givenServiceKeyAndNonMonthlyDay_whenRunDailyNearSync_thenSyncsNearYearRange() {
    NationalHolidaySyncScheduler scheduler = scheduler(true, "2026-06-02T19:00:00Z");

    scheduler.syncDailyNearRange();

    verify(syncNationalHolidaysUseCase).syncYearRange(2026, 2028);
  }

  @Test
  @DisplayName("월초에는 daily near sync를 호출하지 않는다")
  void givenFirstDayOfMonth_whenRunDailyNearSync_thenSkipsSyncYearRange() {
    NationalHolidaySyncScheduler scheduler = scheduler(true, "2026-05-31T15:00:00Z");

    scheduler.syncDailyNearRange();

    verifyNoInteractions(syncNationalHolidaysUseCase);
  }

  @Test
  @DisplayName("service key가 없으면 scheduler는 동기화 UseCase를 호출하지 않는다")
  void givenMissingServiceKey_whenRunScheduledSync_thenSkipsSyncUseCase() {
    NationalHolidaySyncScheduler scheduler = scheduler(false, "2026-06-02T19:00:00Z");

    scheduler.syncMonthlyFullRange();
    scheduler.syncDailyNearRange();

    verifyNoInteractions(syncNationalHolidaysUseCase);
  }

  private NationalHolidaySyncScheduler scheduler(boolean hasServiceKey, String instant) {
    HolidayApiProperties properties = new HolidayApiProperties();
    properties.setServiceKey(hasServiceKey ? "test-service-key" : "");
    return new NationalHolidaySyncScheduler(
        syncNationalHolidaysUseCase,
        properties,
        Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul")));
  }
}
