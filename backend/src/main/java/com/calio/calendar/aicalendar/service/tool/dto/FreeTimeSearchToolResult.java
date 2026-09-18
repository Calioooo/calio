package com.calio.calendar.aicalendar.service.tool.dto;

import com.calio.calendar.singleevent.service.dto.CalendarFreeTime;
import java.util.List;

public record FreeTimeSearchToolResult(List<CalendarFreeTime> freeTimes) {
}
