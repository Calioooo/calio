package com.calio.calendar.holiday.service;

import com.calio.calendar.holiday.domain.NationalHoliday;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NationalHolidayCommandService {

    private final NationalHolidayRepository nationalHolidayRepository;

    public NationalHolidayCommandService(NationalHolidayRepository nationalHolidayRepository) {
        this.nationalHolidayRepository = nationalHolidayRepository;
    }

    public void replaceSnapshot(
            List<NationalHolidayProviderRow> missingProviderRows,
            List<NationalHoliday> staleHolidays
    ) {
        List<NationalHoliday> missingHolidays = missingProviderRows.stream()
                .map(providerRow -> new NationalHoliday(
                        providerRow.holidayDate(),
                        providerRow.holidayTitle()
                ))
                .toList();
        nationalHolidayRepository.saveAllAndFlush(missingHolidays);
        nationalHolidayRepository.deleteAll(staleHolidays);
    }
}
