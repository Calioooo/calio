package com.calio.calendar.tag.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.account.domain.Account;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

class TagTest {

    @Test
    @DisplayName("CUSTOM 태그는 제목과 색상 코드를 수정할 수 있다")
    void givenCustomTag_whenUpdate_thenChangesTitleAndColorCode() {
        // given
        Tag tag = new Tag(TagType.CUSTOM, "기존", "#111111", mock(Account.class));

        // when
        tag.update("변경", "#abcdef");

        // then
        assertThat(tag.getTitle()).isEqualTo("변경");
        assertThat(tag.getColorCode()).isEqualTo("#ABCDEF");
    }

    @Test
    @DisplayName("DEFAULT 태그는 entity 내부에서도 수정할 수 없다")
    void givenDefaultTag_whenUpdate_thenThrowsValidationFailed() {
        // given
        Tag tag = new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B");

        // when, then
        assertThatThrownBy(() -> tag.update("변경", "#000000"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED)
                );
    }
}
