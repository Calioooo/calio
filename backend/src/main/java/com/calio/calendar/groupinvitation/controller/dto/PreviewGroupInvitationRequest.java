package com.calio.calendar.groupinvitation.controller.dto;

import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PreviewGroupInvitationRequest(
        @NotNull(message = "초대 인증 유형은 필수입니다.") InvitationCredentialType credentialType,
        @NotBlank(message = "초대 인증 정보는 공백일 수 없습니다.") String credential
) {

    @Override
    public String toString() {
        return "PreviewGroupInvitationRequest[credentialType=%s, credential=REDACTED]"
                .formatted(credentialType);
    }
}
