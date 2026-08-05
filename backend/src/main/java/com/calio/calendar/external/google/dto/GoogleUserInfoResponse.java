package com.calio.calendar.external.google.dto;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GoogleUserInfoResponse(
        String subject,
        String email
) {

    public GoogleUserInfoResponse {
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new CalioException(ErrorCode.GOOGLE_USER_INFO_INVALID);
        }
    }

    @JsonCreator
    public GoogleUserInfoResponse(
            @JsonProperty("sub") String subject,
            @JsonProperty("email") String email,
            @JsonProperty("email_verified") Boolean emailVerified
    ) {
        this(subject, email);
        if (Boolean.FALSE.equals(emailVerified)) {
            throw new CalioException(ErrorCode.GOOGLE_USER_INFO_INVALID);
        }
    }
}
