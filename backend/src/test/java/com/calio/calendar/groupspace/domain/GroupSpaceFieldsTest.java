package com.calio.calendar.groupspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupSpaceFieldsTest {

    @Test
    @DisplayName("name은 trim 후 NFC로 정규화하고 Unicode code point 30자까지 허용한다")
    void normalizeNameAppliesTrimAndNfcUsingCodePointLength() {
        // given
        String name = "  " + "가".repeat(29) + "e\u0301  ";

        // when
        String normalized = GroupSpaceFields.normalizeName(name);

        // then
        assertThat(normalized).isEqualTo("가".repeat(29) + "é");
        assertThat(normalized.codePointCount(0, normalized.length())).isEqualTo(30);
    }

    @Test
    @DisplayName("name은 빈 값, 30 code point 초과, control character를 거부한다")
    void invalidNamesAreRejected() {
        assertValidationFailed(() -> GroupSpaceFields.normalizeName("  "));
        assertValidationFailed(() -> GroupSpaceFields.normalizeName("가".repeat(31)));
        assertValidationFailed(() -> GroupSpaceFields.normalizeName("group\nname"));
    }

    @Test
    @DisplayName("nickname은 NFC 정규화 후 영문, 숫자, 완성형 한글 1~9자만 허용한다")
    void normalizeNicknameAppliesNfcBeforePatternValidation() {
        assertThat(GroupSpaceFields.normalizeNickname("가123")).isEqualTo("가123");
        assertThat(GroupSpaceFields.normalizeNickname("Calio123")).isEqualTo("Calio123");
    }

    @Test
    @DisplayName("nickname은 공백, 특수문자와 9자 초과 값을 거부한다")
    void invalidNicknamesAreRejected() {
        assertValidationFailed(() -> GroupSpaceFields.normalizeNickname("nick name"));
        assertValidationFailed(() -> GroupSpaceFields.normalizeNickname("nick!"));
        assertValidationFailed(() -> GroupSpaceFields.normalizeNickname("Cafe\u0301"));
        assertValidationFailed(() -> GroupSpaceFields.normalizeNickname("가".repeat(10)));
    }

    @Test
    @DisplayName("non-empty emoji는 whitespace와 Unicode sequence를 포함한 원문을 보존한다")
    void emojiIsOpaqueAndPreserved() {
        assertThat(GroupSpaceFields.canonicalizeEmoji(" ")).isEqualTo(" ");
        assertThat(GroupSpaceFields.canonicalizeEmoji("👩🏽‍💻")).isEqualTo("👩🏽‍💻");
        assertThat(GroupSpaceFields.canonicalizeEmoji("e\u0301")).isEqualTo("e\u0301");
        assertThat(GroupSpaceFields.canonicalizeEmoji("é")).isEqualTo("é");
    }

    @Test
    @DisplayName("emoji는 null과 empty string을 null로 만들고 64 code point까지만 허용한다")
    void emojiCanonicalizationUsesCodePointLength() {
        String maximumLengthEmoji = "😀".repeat(64);

        assertThat(GroupSpaceFields.canonicalizeEmoji(null)).isNull();
        assertThat(GroupSpaceFields.canonicalizeEmoji("")).isNull();
        assertThat(GroupSpaceFields.canonicalizeEmoji(maximumLengthEmoji))
                .isEqualTo(maximumLengthEmoji);
        assertValidationFailed(() -> GroupSpaceFields.canonicalizeEmoji("😀".repeat(65)));
    }

    private void assertValidationFailed(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CalioException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
