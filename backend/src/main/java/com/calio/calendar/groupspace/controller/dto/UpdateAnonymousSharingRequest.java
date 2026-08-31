package com.calio.calendar.groupspace.controller.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAnonymousSharingRequest(@NotNull Boolean isAnonymous) {
}
