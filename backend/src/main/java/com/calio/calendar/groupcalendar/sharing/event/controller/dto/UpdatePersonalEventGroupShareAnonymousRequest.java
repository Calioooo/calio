package com.calio.calendar.groupcalendar.sharing.event.controller.dto;

import jakarta.validation.constraints.NotNull;

public record UpdatePersonalEventGroupShareAnonymousRequest(
        @NotNull(message = "익명 공유 여부는 필수입니다.") Boolean anonymous
) {
}
