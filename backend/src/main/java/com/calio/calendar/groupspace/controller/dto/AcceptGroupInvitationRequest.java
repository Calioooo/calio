package com.calio.calendar.groupspace.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AcceptGroupInvitationRequest(
        @NotNull(message = "초대 인증 유형은 필수입니다.") GroupInvitationAcceptCredentialType credentialType,
        @NotBlank(message = "초대 인증 정보는 공백일 수 없습니다.") String credential,
        @NotBlank(message = "그룹 멤버 닉네임은 공백일 수 없습니다.") String nickname
) {
}
