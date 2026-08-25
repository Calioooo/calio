package com.calio.calendar.groupcalendar.sharing.event.controller.dto;

import jakarta.validation.constraints.NotNull;
public record UpdatePersonalEventGroupShareRequest(
        @NotNull(message = "원본 일정 상세 표시 여부는 필수입니다.") Boolean showOriginalDetails
) {
}
