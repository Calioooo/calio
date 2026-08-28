package com.calio.calendar.sharing.event.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreateEventGroupSharesRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("단건 일정 공유 필수 선택값은 한국어 검증 메시지를 반환한다")
    void eventShareRequestUsesKoreanDtoValidationMessagesForRequiredSelections() {
        Set<String> messages = validator.validate(new CreateEventGroupSharesRequest(
                List.of(),
                Arrays.asList((Long) null),
                null
        )).stream().map(violation -> violation.getMessage()).collect(java.util.stream.Collectors.toSet());

        assertThat(messages).containsExactlyInAnyOrder(
                "공유할 일정은 하나 이상 선택해야 합니다.",
                "공유할 그룹 선택에는 빈 값이 포함될 수 없습니다.",
                "익명 공유 여부는 필수입니다."
        );
    }
}
