package com.calio.calendar.holiday.service;

import com.calio.calendar.holiday.domain.NationalHoliday;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NationalHolidayQueryService {

    private final NationalHolidayRepository nationalHolidayRepository;

    public NationalHolidayQueryService(NationalHolidayRepository nationalHolidayRepository) {
        this.nationalHolidayRepository = nationalHolidayRepository;
    }

    public List<NationalHoliday> getNationalHolidays(LocalDate from, LocalDate to) {
        return nationalHolidayRepository.findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(from, to);
    }

    public List<NationalHoliday> getHolidaysInYear(int year) {
        return nationalHolidayRepository.findByHolidayDateBetween(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
        );
    }
}
