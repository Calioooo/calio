package com.calio.calendar.sharing.controller.dto;

import java.util.UUID;

public record GroupShareStatusResponse(
        Long groupSpaceId,
        String groupSpaceName,
        boolean isAnonymous,
        UUID publicShareId
) {
}
