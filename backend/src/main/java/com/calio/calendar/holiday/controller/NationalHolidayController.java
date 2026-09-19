package com.calio.calendar.holiday.controller;

import com.calio.calendar.holiday.controller.dto.NationalHolidayResponse;
import com.calio.calendar.holiday.usecase.GetNationalHolidaysUseCase;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/national-holidays")
public class NationalHolidayController {

  private final GetNationalHolidaysUseCase getNationalHolidaysUseCase;

  public NationalHolidayController(GetNationalHolidaysUseCase getNationalHolidaysUseCase) {
    this.getNationalHolidaysUseCase = getNationalHolidaysUseCase;
  }

  @GetMapping
  public List<NationalHolidayResponse> getNationalHolidays(
      @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return getNationalHolidaysUseCase.get(from, to);
  }
}
