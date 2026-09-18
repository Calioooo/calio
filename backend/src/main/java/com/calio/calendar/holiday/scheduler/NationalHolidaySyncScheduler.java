package com.calio.calendar.holiday.scheduler;

import com.calio.calendar.holiday.client.HolidayApiProperties;
import com.calio.calendar.holiday.usecase.SyncNationalHolidaysUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NationalHolidaySyncScheduler {

  static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

  private static final Logger log = LoggerFactory.getLogger(NationalHolidaySyncScheduler.class);

  private final SyncNationalHolidaysUseCase syncNationalHolidaysUseCase;
  private final HolidayApiProperties holidayApiProperties;
  private final AtomicBoolean syncRunning = new AtomicBoolean(false);
  private final Clock clock;

  @Autowired
  public NationalHolidaySyncScheduler(
      SyncNationalHolidaysUseCase syncNationalHolidaysUseCase,
      HolidayApiProperties holidayApiProperties) {
    this(syncNationalHolidaysUseCase, holidayApiProperties, Clock.system(KOREA_ZONE));
  }

  NationalHolidaySyncScheduler(
      SyncNationalHolidaysUseCase syncNationalHolidaysUseCase,
      HolidayApiProperties holidayApiProperties,
      Clock clock) {
    this.syncNationalHolidaysUseCase = syncNationalHolidaysUseCase;
    this.holidayApiProperties = holidayApiProperties;
    this.clock = clock;
  }

  @Scheduled(cron = "0 0 4 1 * *", zone = "Asia/Seoul")
  public void syncMonthlyFullRange() {
    int currentYear = today().getYear();
    syncIfAvailable(currentYear - 20, currentYear + 20);
  }

  @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
  public void syncDailyNearRange() {
    LocalDate today = today();
    if (today.getDayOfMonth() == 1) {
      log.info("National holiday daily sync skipped on monthly sync day. date={}", today);
      return;
    }

    int currentYear = today.getYear();
    syncIfAvailable(currentYear, currentYear + 2);
  }

  private void syncIfAvailable(int startYear, int endYear) {
    if (!holidayApiProperties.hasServiceKey()) {
      log.info(
          "National holiday sync skipped because service key is missing. startYear={} endYear={}",
          startYear,
          endYear);
      return;
    }

    if (!syncRunning.compareAndSet(false, true)) {
      log.info(
          "National holiday sync skipped because another sync is running. startYear={} endYear={}",
          startYear,
          endYear);
      return;
    }

    try {
      syncNationalHolidaysUseCase.syncYearRange(startYear, endYear);
    } catch (Exception exception) {
      log.error(
          "National holiday scheduled sync failed. startYear={} endYear={}",
          startYear,
          endYear,
          exception);
    } finally {
      syncRunning.set(false);
    }
  }

  private LocalDate today() {
    return LocalDate.now(clock);
  }
}
