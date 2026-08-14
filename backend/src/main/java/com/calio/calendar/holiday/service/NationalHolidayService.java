package com.calio.calendar.holiday.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.holiday.client.HolidayApiClient;
import com.calio.calendar.holiday.client.dto.HolidayApiItem;
import com.calio.calendar.holiday.client.dto.HolidayApiResponse;
import com.calio.calendar.holiday.controller.dto.NationalHolidayResponse;
import com.calio.calendar.holiday.domain.NationalHoliday;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NationalHolidayService {

    private static final Logger log = LoggerFactory.getLogger(NationalHolidayService.class);
    private static final DateTimeFormatter PROVIDER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final HolidayApiClient holidayApiClient;
    private final NationalHolidayQueryService nationalHolidayQueryService;
    private final NationalHolidayCommandService nationalHolidayCommandService;

    public NationalHolidayService(
            HolidayApiClient holidayApiClient,
            NationalHolidayQueryService nationalHolidayQueryService,
            NationalHolidayCommandService nationalHolidayCommandService
    ) {
        this.holidayApiClient = holidayApiClient;
        this.nationalHolidayQueryService = nationalHolidayQueryService;
        this.nationalHolidayCommandService = nationalHolidayCommandService;
    }

    @Transactional(readOnly = true)
    public List<NationalHolidayResponse> getNationalHolidays(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        return nationalHolidayQueryService.getNationalHolidays(from, to)
                .stream()
                .map(NationalHolidayResponse::from)
                .toList();
    }

    public void syncYearRange(int startYear, int endYear) {
        for (int year = startYear; year <= endYear; year++) {
            syncYear(year);
        }
    }

    private void syncYear(int year) {
        HolidayApiResponse response = null;
        try {
            response = holidayApiClient.fetchHolidays(year);
            if (!response.isSuccess()) {
                log.warn("National holiday sync failed. year={} resultCode={}", year, response.resultCode());
                return;
            }

            Set<NationalHolidayProviderRow> providerRows = toProviderRows(year, response.items());
            if (providerRows.isEmpty()) {
                log.warn("National holiday sync returned empty provider rows. year={}", year);
                return;
            }

            applySnapshot(year, providerRows);
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

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (!from.isAfter(to)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }

    private void applySnapshot(int year, Set<NationalHolidayProviderRow> providerRows) {
        List<NationalHoliday> existingHolidays = nationalHolidayQueryService.getHolidaysInYear(year);
        Set<NationalHolidayProviderRow> existingRows = existingHolidays.stream()
                .map(NationalHolidayProviderRow::from)
                .collect(Collectors.toSet());

        List<NationalHolidayProviderRow> missingProviderRows = providerRows.stream()
                .filter(providerRow -> !existingRows.contains(providerRow))
                .toList();
        List<NationalHoliday> staleHolidays = existingHolidays.stream()
                .filter(holiday -> !providerRows.contains(NationalHolidayProviderRow.from(holiday)))
                .toList();
        nationalHolidayCommandService.replaceSnapshot(missingProviderRows, staleHolidays);
    }

    private String errorCode(Exception exception) {
        if (exception instanceof CalioException calioException) {
            return calioException.getErrorCode().name();
        }
        return null;
    }

    private Set<NationalHolidayProviderRow> toProviderRows(int year, List<HolidayApiItem> items) {
        return items.stream()
                .filter(item -> "Y".equals(item.isHoliday()))
                .map(this::toHolidayRow)
                .peek(providerRow -> requireRequestedYear(year, providerRow))
                .collect(Collectors.toSet());
    }

    private void requireRequestedYear(int year, NationalHolidayProviderRow providerRow) {
        if (providerRow.holidayDate().getYear() != year) {
            throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
        }
    }

    private NationalHolidayProviderRow toHolidayRow(HolidayApiItem item) {
        return new NationalHolidayProviderRow(
                LocalDate.parse(item.localDate(), PROVIDER_DATE_FORMAT),
                item.dateName()
        );
    }
}
