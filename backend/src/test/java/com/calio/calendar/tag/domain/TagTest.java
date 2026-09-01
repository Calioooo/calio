package com.calio.calendar.tag.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.account.domain.Account;
import com.calio.calendar.groupspace.domain.GroupSpace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

class TagTest {

    @Test
    @DisplayName("태그 정적 팩터리는 각 소유 범위에 맞는 타입과 소유자만 생성한다")
    void factoriesCreateTagsInTheirOwnershipScopes() {
        Account account = new Account();
        GroupSpace groupSpace = new GroupSpace(1L, "그룹", null);

        Tag personalDefault = Tag.personalDefault("기타", "#64748B");
        Tag personalCustom = Tag.personalCustom(account, "개인", "#64748B");
        Tag groupDefault = Tag.groupDefault(groupSpace);
        Tag groupCustom = Tag.groupCustom(groupSpace, "업무", "#64748B");

        assertThat(personalDefault.getTagType()).isEqualTo(TagType.PERSONAL_DEFAULT);
        assertThat(personalDefault.getAccount()).isNull();
        assertThat(personalDefault.getGroupSpace()).isNull();
        assertThat(personalCustom.getAccount()).isSameAs(account);
        assertThat(personalCustom.getGroupSpace()).isNull();
        assertThat(groupDefault.getTagType()).isEqualTo(TagType.GROUP_DEFAULT);
        assertThat(groupDefault.getTitle()).isEqualTo("기타");
        assertThat(groupCustom.getAccount()).isNull();
        assertThat(groupCustom.getGroupSpace()).isSameAs(groupSpace);
    }

    @Test
    @DisplayName("CUSTOM 태그는 제목과 색상 코드를 수정할 수 있다")
    void givenCustomTag_whenUpdate_thenChangesTitleAndColorCode() {
        // given
        Tag tag = Tag.personalCustom(mock(Account.class), "기존", "#111111");

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

}
