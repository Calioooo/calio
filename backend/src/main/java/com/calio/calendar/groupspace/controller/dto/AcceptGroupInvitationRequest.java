package com.calio.calendar.groupspace.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AcceptGroupInvitationRequest(
        @NotNull GroupInvitationAcceptCredentialType credentialType,
        @NotBlank String credential,
        @NotBlank String nickname
) {
}
