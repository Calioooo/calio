package com.calio.calendar.sharing.controller.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateGroupShareAnonymousRequest(
        @NotNull(message = "익명 공유 여부는 필수입니다.") Boolean isAnonymous
) {
}
