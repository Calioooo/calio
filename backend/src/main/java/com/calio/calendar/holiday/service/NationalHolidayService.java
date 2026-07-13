package com.calio.calendar.holiday.service;

import com.calio.calendar.holiday.controller.dto.NationalHolidayResponse;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NationalHolidayService {

    private final NationalHolidayRepository nationalHolidayRepository;

    public NationalHolidayService(NationalHolidayRepository nationalHolidayRepository) {
        this.nationalHolidayRepository = nationalHolidayRepository;
    }

    @Transactional(readOnly = true)
    public List<NationalHolidayResponse> getNationalHolidays(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        return nationalHolidayRepository.findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(from, to)
                .stream()
                .map(NationalHolidayResponse::from)
                .toList();
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (!from.isAfter(to)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }
}
