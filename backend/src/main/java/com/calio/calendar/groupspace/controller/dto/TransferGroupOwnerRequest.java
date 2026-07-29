package com.calio.calendar.groupspace.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferGroupOwnerRequest(@NotNull @Positive Long targetMemberId) {
}
