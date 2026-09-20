package com.calio.calendar.singleevent.service.dto;

import java.util.List;

public record CalendarFreeTime(String start, String end, List<String> allDayNotices) {}
