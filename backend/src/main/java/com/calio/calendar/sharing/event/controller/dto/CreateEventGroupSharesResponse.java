package com.calio.calendar.sharing.event.controller.dto;

import java.util.List;

public record CreateEventGroupSharesResponse(List<EventGroupShareResultResponse> results) {

    public static CreateEventGroupSharesResponse from(List<EventGroupShareResultResponse> results) {
        return new CreateEventGroupSharesResponse(results);
    }
}
