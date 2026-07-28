package com.calio.calendar.groupinvitation.controller.dto;

import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PreviewGroupInvitationRequest(
        @NotNull InvitationCredentialType credentialType,
        @NotBlank String credential
) {

    @Override
    public String toString() {
        return "PreviewGroupInvitationRequest[credentialType=%s, credential=REDACTED]"
                .formatted(credentialType);
    }
}
