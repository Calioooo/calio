package com.calio.calendar.holiday.service;

import com.calio.calendar.holiday.client.HolidayApiClient;
import com.calio.calendar.holiday.client.dto.HolidayApiItem;
import com.calio.calendar.holiday.client.dto.HolidayApiResponse;
import com.calio.calendar.common.error.CalioException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NationalHolidaySyncService {

    private static final Logger log = LoggerFactory.getLogger(NationalHolidaySyncService.class);
    private static final DateTimeFormatter PROVIDER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final HolidayApiClient holidayApiClient;
    private final NationalHolidayPersistenceService nationalHolidayPersistenceService;

    public NationalHolidaySyncService(
            HolidayApiClient holidayApiClient,
            NationalHolidayPersistenceService nationalHolidayPersistenceService
    ) {
        this.holidayApiClient = holidayApiClient;
        this.nationalHolidayPersistenceService = nationalHolidayPersistenceService;
    }

    public void syncYearRange(int startYear, int endYear) {
        for (int year = startYear; year <= endYear; year++) {
            syncYear(year);
        }
    }

    public void syncYear(int year) {
        HolidayApiResponse response = null;
        try {
            response = holidayApiClient.fetchHolidays(year);
            if (!response.isSuccess()) {
                log.warn("National holiday sync failed. year={} resultCode={}", year, response.resultCode());
                return;
            }

            Set<NationalHolidayProviderRow> providerRows = toProviderRows(response.items());
            if (providerRows.isEmpty()) {
                log.warn("National holiday sync returned empty provider rows. year={}", year);
                return;
            }

            nationalHolidayPersistenceService.applySnapshot(year, providerRows);
        } catch (Exception exception) {
            log.warn(
                    "National holiday sync failed. year={} resultCode={} errorCode={} message={}",
                    year,
                    response == null ? null : response.resultCode(),
                    errorCode(exception),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private String errorCode(Exception exception) {
        if (exception instanceof CalioException calioException) {
            return calioException.getErrorCode().name();
        }
        return null;
    }

    private Set<NationalHolidayProviderRow> toProviderRows(List<HolidayApiItem> items) {
        return items.stream()
                .filter(item -> "Y".equals(item.isHoliday()))
                .map(this::toHolidayRow)
                .collect(Collectors.toSet());
    }

    private NationalHolidayProviderRow toHolidayRow(HolidayApiItem item) {
        return new NationalHolidayProviderRow(
                LocalDate.parse(item.locdate(), PROVIDER_DATE_FORMAT),
                item.dateName()
        );
    }
}
