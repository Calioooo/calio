package com.calio.calendar.holiday.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.holiday.controller.dto.NationalHolidayResponse;
import com.calio.calendar.holiday.repository.NationalHolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetNationalHolidaysUseCase {

  private final NationalHolidayRepository nationalHolidayRepository;

  public GetNationalHolidaysUseCase(NationalHolidayRepository nationalHolidayRepository) {
    this.nationalHolidayRepository = nationalHolidayRepository;
  }

  @Transactional(readOnly = true)
  public List<NationalHolidayResponse> get(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new CalioException(ErrorCode.INVALID_TIME_RANGE);
    }

    return nationalHolidayRepository
        .findByHolidayDateBetweenOrderByHolidayDateAscHolidayTitleAsc(from, to)
        .stream()
        .map(NationalHolidayResponse::from)
        .toList();
  }
}
