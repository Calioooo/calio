package com.calio.calendar.holiday.service;

import com.calio.calendar.holiday.domain.NationalHoliday;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NationalHolidayCommandService {

    private final NationalHolidayRepository nationalHolidayRepository;

    public NationalHolidayCommandService(NationalHolidayRepository nationalHolidayRepository) {
        this.nationalHolidayRepository = nationalHolidayRepository;
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
        return nationalHolidayRepository.findByHolidayDateBetween(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
        );
    }

    private void insertIfMissing(NationalHolidayProviderRow providerRow) {
        if (nationalHolidayRepository.existsByHolidayDateAndHolidayTitle(
                providerRow.holidayDate(),
                providerRow.holidayTitle()
        )) {
            return;
        }

        nationalHolidayRepository.saveAndFlush(
                new NationalHoliday(providerRow.holidayDate(), providerRow.holidayTitle())
        );
    }

    private void deleteStaleHolidays(List<NationalHoliday> staleHolidays) {
        nationalHolidayRepository.deleteAll(staleHolidays);
    }
}
