package com.calio.calendar.controller;

import com.calio.calendar.controller.dto.NationalHolidayResponse;
import com.calio.calendar.service.NationalHolidayService;
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

    private final NationalHolidayService nationalHolidayService;

    public NationalHolidayController(NationalHolidayService nationalHolidayService) {
        this.nationalHolidayService = nationalHolidayService;
    }

    @GetMapping
    public List<NationalHolidayResponse> listNationalHolidays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return nationalHolidayService.listNationalHolidays(from, to);
    }
}
