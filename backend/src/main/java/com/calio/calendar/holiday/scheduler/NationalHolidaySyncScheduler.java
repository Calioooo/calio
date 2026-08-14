package com.calio.calendar.holiday.scheduler;

import com.calio.calendar.holiday.client.HolidayApiProperties;
import com.calio.calendar.holiday.service.NationalHolidayService;
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

    private final NationalHolidayService nationalHolidayService;
    private final HolidayApiProperties holidayApiProperties;
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);
    private final Clock clock;

    @Autowired
    public NationalHolidaySyncScheduler(
            NationalHolidayService nationalHolidayService,
            HolidayApiProperties holidayApiProperties
    ) {
        this(nationalHolidayService, holidayApiProperties, Clock.system(KOREA_ZONE));
    }

    NationalHolidaySyncScheduler(
            NationalHolidayService nationalHolidayService,
            HolidayApiProperties holidayApiProperties,
            Clock clock
    ) {
        this.nationalHolidayService = nationalHolidayService;
        this.holidayApiProperties = holidayApiProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 1 * *", zone = "Asia/Seoul")
    public void syncMonthlyFullRange() {
        runIfAvailable("monthly-full", () -> {
            int currentYear = currentYear();
            nationalHolidayService.syncYearRange(currentYear - 20, currentYear + 20);
        });
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void syncDailyNearRange() {
        LocalDate today = today();
        if (today.getDayOfMonth() == 1) {
            log.info("National holiday daily sync skipped on monthly sync day. date={}", today);
            return;
        }

        runIfAvailable("daily-near", () -> {
            int currentYear = today.getYear();
            nationalHolidayService.syncYearRange(currentYear, currentYear + 2);
        });
    }

    private void runIfAvailable(String jobName, Runnable syncJob) {
        if (!holidayApiProperties.hasServiceKey()) {
            log.info("National holiday sync skipped because service key is missing. job={}", jobName);
            return;
        }

        if (!syncRunning.compareAndSet(false, true)) {
            log.info("National holiday sync skipped because another sync is running. job={}", jobName);
            return;
        }

        try {
            syncJob.run();
        } catch (Exception exception) {
            log.error(
                    "National holiday scheduled sync failed. job={} message={}",
                    jobName,
                    exception.getMessage(),
                    exception
            );
        } finally {
            syncRunning.set(false);
        }
    }

    private int currentYear() {
        return today().getYear();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
