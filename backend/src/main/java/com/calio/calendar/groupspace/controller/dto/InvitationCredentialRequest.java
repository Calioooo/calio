package com.calio.calendar.groupspace.controller.dto;

import com.calio.calendar.groupspace.domain.InvitationCredentialType;
import jakarta.validation.constraints.NotNull;

public record InvitationCredentialRequest(
        @NotNull InvitationCredentialType credentialType,
        @NotNull String credential
) {
}
