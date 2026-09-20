package com.calio.calendar.tag.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TagTest {

    @Test
    @DisplayName("태그 정적 팩터리는 각 소유 범위에 맞는 타입과 소유자만 생성한다")
    void factoriesCreateTagsInTheirOwnershipScopes() {
        Long groupSpaceId = 2L;

        Tag personalDefault = Tag.personalDefault("기타", "#64748B");
        Tag personalCustom = Tag.personalCustom(1L, "개인", "#64748B");
        Tag groupDefault = Tag.groupDefault(groupSpaceId);
        Tag groupCustom = Tag.groupCustom(groupSpaceId, "업무", "#64748B");

        assertThat(personalDefault.getTagType()).isEqualTo(TagType.PERSONAL_DEFAULT);
        assertThat(personalDefault.getAccountId()).isNull();
        assertThat(personalDefault.getGroupSpaceId()).isNull();
        assertThat(personalCustom.getAccountId()).isEqualTo(1L);
        assertThat(personalCustom.getGroupSpaceId()).isNull();
        assertThat(groupDefault.getTagType()).isEqualTo(TagType.GROUP_DEFAULT);
        assertThat(groupDefault.getTitle()).isEqualTo("기타");
        assertThat(groupCustom.getAccountId()).isNull();
        assertThat(groupCustom.getGroupSpaceId()).isEqualTo(groupSpaceId);
    }

    @Test
    @DisplayName("CUSTOM 태그는 제목과 색상 코드를 수정할 수 있다")
    void givenCustomTag_whenUpdate_thenChangesTitleAndColorCode() {
        // given
        Tag tag = Tag.personalCustom(1L, "기존", "#111111");

        // when
        tag.update("변경", "#abcdef");

        // then
        assertThat(tag.getTitle()).isEqualTo("변경");
        assertThat(tag.getColorCode()).isEqualTo("#ABCDEF");
    }

    @Test
    @DisplayName("PERSONAL_DEFAULT 태그는 entity 내부에서도 수정할 수 없다")
    void givenPersonalDefaultTag_whenUpdate_thenThrowsValidationFailed() {
        // given
        Tag tag = Tag.personalDefault("기타", "#64748B");

        // when, then
        assertThatThrownBy(() -> tag.update("변경", "#000000"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED)
                );
    }

    @Test
    @DisplayName("태그 제목은 최대 20자까지 허용한다")
    void givenTagTitleLongerThanMaximum_whenCreate_thenThrowsValidationFailed() {
        Tag tag = Tag.personalDefault("😀".repeat(20), "#64748B");

        assertThat(tag.getTitle()).isEqualTo("😀".repeat(20));
        assertThatThrownBy(() -> Tag.personalDefault("가".repeat(21), "#64748B"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED)
                );
    }

}
