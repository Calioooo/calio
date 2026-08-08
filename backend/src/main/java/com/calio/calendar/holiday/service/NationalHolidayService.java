package com.calio.calendar.holiday.service;

import com.calio.calendar.holiday.controller.dto.NationalHolidayResponse;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NationalHolidayService {

    private final NationalHolidayQueryService nationalHolidayQueryService;

    public NationalHolidayService(NationalHolidayQueryService nationalHolidayQueryService) {
        this.nationalHolidayQueryService = nationalHolidayQueryService;
    }

    public List<NationalHolidayResponse> getNationalHolidays(LocalDate from, LocalDate to) {
        validateDateRange(from, to);
        return nationalHolidayQueryService.getNationalHolidays(from, to);
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (!from.isAfter(to)) {
            return;
        }

        throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }
}
