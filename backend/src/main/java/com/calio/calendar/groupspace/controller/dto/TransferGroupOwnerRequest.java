package com.calio.calendar.groupspace.controller.dto;

import jakarta.validation.constraints.NotNull;

public record TransferGroupOwnerRequest(@NotNull Long targetMemberId) {
}
