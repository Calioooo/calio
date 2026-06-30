package com.calio.calendar.service;

import com.calio.calendar.client.HolidayApiClient;
import com.calio.calendar.client.dto.HolidayApiItem;
import com.calio.calendar.client.dto.HolidayApiResponse;
import com.calio.calendar.repository.NationalHolidayRepository;
import com.calio.calendar.repository.entity.NationalHoliday;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NationalHolidaySyncService {

    private static final Logger log = LoggerFactory.getLogger(NationalHolidaySyncService.class);
    private static final DateTimeFormatter PROVIDER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final HolidayApiClient holidayApiClient;
    private final NationalHolidayRepository nationalHolidayRepository;
    private final TransactionTemplate transactionTemplate;

    public NationalHolidaySyncService(
            HolidayApiClient holidayApiClient,
            NationalHolidayRepository nationalHolidayRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.holidayApiClient = holidayApiClient;
        this.nationalHolidayRepository = nationalHolidayRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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

            Set<HolidayRow> providerRows = toProviderRows(response.items());
            if (providerRows.isEmpty()) {
                log.warn("National holiday sync returned empty provider rows. year={}", year);
                return;
            }

            applySnapshot(year, providerRows);
        } catch (Exception exception) {
            log.warn(
                    "National holiday sync failed. year={} resultCode={} message={}",
                    year,
                    response == null ? null : response.resultCode(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private Set<HolidayRow> toProviderRows(List<HolidayApiItem> items) {
        return items.stream()
                .filter(item -> "Y".equals(item.isHoliday()))
                .map(this::toHolidayRow)
                .collect(Collectors.toSet());
    }

    private HolidayRow toHolidayRow(HolidayApiItem item) {
        return new HolidayRow(
                LocalDate.parse(item.locdate(), PROVIDER_DATE_FORMAT),
                item.dateName()
        );
    }

    private void applySnapshot(int year, Set<HolidayRow> providerRows) {
        List<NationalHoliday> existingHolidays = findExistingHolidays(year);

        for (HolidayRow providerRow : providerRows) {
            insertIfMissing(providerRow);
        }

        List<NationalHoliday> staleHolidays = existingHolidays.stream()
                .filter(holiday -> !providerRows.contains(HolidayRow.from(holiday)))
                .toList();
        deleteStaleHolidays(staleHolidays);
    }

    private List<NationalHoliday> findExistingHolidays(int year) {
        return transactionTemplate.execute(status -> nationalHolidayRepository.findByHolidayDateBetween(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
        ));
    }

    private void insertIfMissing(HolidayRow providerRow) {
        transactionTemplate.executeWithoutResult(status -> insertIfMissing(providerRow, status));
    }

    private void insertIfMissing(HolidayRow providerRow, TransactionStatus status) {
        if (nationalHolidayRepository.existsByHolidayDateAndHolidayTitle(
                providerRow.holidayDate(),
                providerRow.holidayTitle()
        )) {
            return;
        }

        try {
            nationalHolidayRepository.saveAndFlush(
                    new NationalHoliday(providerRow.holidayDate(), providerRow.holidayTitle())
            );
        } catch (DuplicateKeyException exception) {
            status.setRollbackOnly();
            logDuplicateInsert(providerRow, exception);
        } catch (DataIntegrityViolationException exception) {
            status.setRollbackOnly();
            logDuplicateInsert(providerRow, exception);
        }
    }

    private void deleteStaleHolidays(List<NationalHoliday> staleHolidays) {
        transactionTemplate.executeWithoutResult(status -> nationalHolidayRepository.deleteAll(staleHolidays));
    }

    private void logDuplicateInsert(HolidayRow providerRow, RuntimeException exception) {
        log.warn(
                "National holiday duplicate insert ignored. holidayDate={} holidayTitle={} message={}",
                providerRow.holidayDate(),
                providerRow.holidayTitle(),
                exception.getMessage()
        );
    }

    private record HolidayRow(LocalDate holidayDate, String holidayTitle) {

        private static HolidayRow from(NationalHoliday holiday) {
            return new HolidayRow(holiday.getHolidayDate(), holiday.getHolidayTitle());
        }
    }
}
