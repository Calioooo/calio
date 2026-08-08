package com.calio.calendar.holiday.service;

import com.calio.calendar.holiday.controller.dto.NationalHolidayResponse;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NationalHolidayQueryService {

    private final NationalHolidayRepository nationalHolidayRepository;

    public NationalHolidayQueryService(NationalHolidayRepository nationalHolidayRepository) {
        this.nationalHolidayRepository = nationalHolidayRepository;
    }

    public List<NationalHolidayResponse> getNationalHolidays(LocalDate from, LocalDate to) {
        return nationalHolidayRepository.findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(from, to)
                .stream()
                .map(NationalHolidayResponse::from)
                .toList();
    }
}
