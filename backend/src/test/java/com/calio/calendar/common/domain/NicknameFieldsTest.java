package com.calio.calendar.common.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NicknameFieldsTest {

    @Test
    @DisplayName("닉네임은 기존 Group Space 정책대로 영문·숫자·한글 1~9자만 허용한다")
    void normalizeAcceptsExistingGroupNicknamePolicy() {
        assertThat(NicknameFields.normalize("calio가나다")).isEqualTo("calio가나다");
    }

    @Test
    @DisplayName("닉네임은 공백, 특수문자, 10자 이상을 허용하지 않는다")
    void normalizeRejectsInvalidNickname() {
        assertThatThrownBy(() -> NicknameFields.normalize("calio user"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED)
                );
        assertThatThrownBy(() -> NicknameFields.normalize("calio-user"))
                .isInstanceOf(CalioException.class);
        assertThatThrownBy(() -> NicknameFields.normalize("1234567890"))
                .isInstanceOf(CalioException.class);
    }
}
