package com.calio.calendar.controller;

import com.calio.calendar.controller.dto.CalendarItemResponse;
import com.calio.calendar.service.CalendarItemService;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/calendar-items")
public class CalendarItemController {

    private final CalendarItemService calendarItemService;

    public CalendarItemController(CalendarItemService calendarItemService) {
        this.calendarItemService = calendarItemService;
    }

    @GetMapping
    public List<CalendarItemResponse> listCalendarItems(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return calendarItemService.listCalendarItems(from, to);
    }
}
