package com.calio.calendar.holiday.service;

import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import com.calio.calendar.holiday.domain.NationalHoliday;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NationalHolidayPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(NationalHolidayPersistenceService.class);

    private final NationalHolidayRepository nationalHolidayRepository;
    private final TransactionTemplate transactionTemplate;

    public NationalHolidayPersistenceService(
            NationalHolidayRepository nationalHolidayRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.nationalHolidayRepository = nationalHolidayRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void applySnapshot(int year, Set<NationalHolidayProviderRow> providerRows) {
        List<NationalHoliday> existingHolidays = findExistingHolidays(year);

        for (NationalHolidayProviderRow providerRow : providerRows) {
            insertIfMissing(providerRow);
        }

        List<NationalHoliday> staleHolidays = existingHolidays.stream()
                .filter(holiday -> !providerRows.contains(NationalHolidayProviderRow.from(holiday)))
                .toList();
        deleteStaleHolidays(staleHolidays);
    }

    private List<NationalHoliday> findExistingHolidays(int year) {
        return transactionTemplate.execute(status -> nationalHolidayRepository.findByHolidayDateBetween(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
        ));
    }

    private void insertIfMissing(NationalHolidayProviderRow providerRow) {
        transactionTemplate.executeWithoutResult(status -> insertIfMissing(providerRow, status));
    }

    private void insertIfMissing(NationalHolidayProviderRow providerRow, TransactionStatus status) {
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
        } catch (DataIntegrityViolationException exception) {
            status.setRollbackOnly();
            logDuplicateInsert(providerRow, exception);
        }
    }

    private void deleteStaleHolidays(List<NationalHoliday> staleHolidays) {
        transactionTemplate.executeWithoutResult(status -> nationalHolidayRepository.deleteAll(staleHolidays));
    }

    private void logDuplicateInsert(NationalHolidayProviderRow providerRow, RuntimeException exception) {
        log.warn(
                "National holiday duplicate insert ignored. holidayDate={} holidayTitle={} message={}",
                providerRow.holidayDate(),
                providerRow.holidayTitle(),
                exception.getMessage()
        );
    }
}
