package com.calio.calendar.groupcalendar.sharing.event.service.dto;

import java.util.List;

public record SelectedPersonalEventGroupShareCommand(List<Long> eventIds) {
}
