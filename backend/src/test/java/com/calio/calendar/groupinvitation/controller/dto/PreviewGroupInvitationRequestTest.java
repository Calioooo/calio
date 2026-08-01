package com.calio.calendar.groupinvitation.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreviewGroupInvitationRequestTest {

    @Test
    @DisplayName("초대 미리보기 요청은 필수 인증 유형과 인증 정보의 validation message를 제공한다")
    void providesValidationMessagesForRequiredCredentials() {
        // given
        PreviewGroupInvitationRequest request = new PreviewGroupInvitationRequest(null, " ");

        // when
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();

            // then
            assertThat(validator.validate(request))
                    .extracting(violation -> violation.getMessage())
                    .containsExactlyInAnyOrder(
                            "초대 인증 유형은 필수입니다.",
                            "초대 인증 정보는 공백일 수 없습니다."
                    );
        }
    }
}
