package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupSpaceInputNormalizerTest {

    private final GroupSpaceInputNormalizer normalizer = new GroupSpaceInputNormalizer();

    @Test
    @DisplayName("그룹 이름은 trim과 NFC 정규화를 거친 canonical 값으로 저장된다")
    void normalizeNameReturnsTrimmedNfcValue() {
        assertThat(normalizer.normalizeName("  Cafe\u0301  ")).isEqualTo("Café");
    }

    @Test
    @DisplayName("nickname은 영문, 숫자와 완성형 한글 1~9자만 허용한다")
    void normalizeNicknameAcceptsOnlyCanonicalNicknameAlphabet() {
        assertThat(normalizer.normalizeNickname("Calio가1")).isEqualTo("Calio가1");
        assertValidationFailed(() -> normalizer.normalizeNickname("name space"));
        assertValidationFailed(() -> normalizer.normalizeNickname("열글자닉네임A1BC"));
    }

    @Test
    @DisplayName("emoji는 하나의 extended grapheme cluster인 pictographic 값만 허용한다")
    void normalizeEmojiValidatesExtendedGraphemeCluster() {
        assertThat(normalizer.normalizeEmoji("👨‍👩‍👧‍👦")).isEqualTo("👨‍👩‍👧‍👦");
        assertValidationFailed(() -> normalizer.normalizeEmoji("🙂🙂"));
        assertValidationFailed(() -> normalizer.normalizeEmoji("A"));
    }

    @Test
    @DisplayName("nullable emoji의 empty string은 null이고 whitespace-only 값은 validation failure다")
    void normalizeEmojiDistinguishesEmptyAndWhitespace() {
        assertThat(normalizer.normalizeEmoji("")).isNull();
        assertThat(normalizer.normalizeEmoji(null)).isNull();
        assertValidationFailed(() -> normalizer.normalizeEmoji(" "));
    }

    private void assertValidationFailed(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED)
                );
    }
}
