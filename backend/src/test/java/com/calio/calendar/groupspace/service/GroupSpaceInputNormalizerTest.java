package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GroupSpaceInputNormalizerTest {

    private final GroupSpaceInputNormalizer normalizer = new GroupSpaceInputNormalizer();

    @Test
    @DisplayName("name은 trim과 NFC 정규화를 적용한 뒤 저장 값으로 반환한다")
    void givenDecomposedAndPaddedName_whenNormalize_thenReturnsTrimmedNfcName() {
        assertThat(normalizer.normalizeName("  가족  ")).isEqualTo("가족");
    }

    @Test
    @DisplayName("nickname은 NFC 정규화 후 영문, 숫자, 완성형 한글만 허용한다")
    void givenDecomposedNickname_whenNormalize_thenReturnsComposedNickname() {
        assertThat(normalizer.normalizeNickname("가1")).isEqualTo("가1");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validEmoji")
    @DisplayName("하나의 RGI 또는 emoji grapheme cluster는 원형을 보존한다")
    void givenValidEmoji_whenNormalize_thenPreservesEmoji(String description, String emoji) {
        assertThat(normalizer.normalizeEmoji(emoji)).isEqualTo(emoji);
    }

    private static Stream<Arguments> validEmoji() {
        return Stream.of(
                Arguments.of("variation selector", "☀️"),
                Arguments.of("skin tone", "👍🏽"),
                Arguments.of("flag", "🇰🇷"),
                Arguments.of("ZWJ", "👨‍👩‍👧‍👦"),
                Arguments.of("keycap 1 (U+0031 U+FE0F U+20E3)", "1️⃣")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidInputs")
    @DisplayName("계약을 벗어난 name, nickname, emoji는 VALIDATION_FAILED로 거절한다")
    void givenInvalidInput_whenNormalize_thenThrowsValidationFailed(
            String description,
            ThrowingNormalization normalization
    ) {
        assertThatThrownBy(normalization::normalize)
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED)
                );
    }

    private static Stream<Arguments> invalidInputs() {
        GroupSpaceInputNormalizer normalizer = new GroupSpaceInputNormalizer();
        return Stream.of(
                Arguments.of("empty name", (ThrowingNormalization) () -> normalizer.normalizeName("  ")),
                Arguments.of("control in name", (ThrowingNormalization) () -> normalizer.normalizeName("a\nb")),
                Arguments.of("nickname whitespace", (ThrowingNormalization) () -> normalizer.normalizeNickname("가 나")),
                Arguments.of("nickname jamo", (ThrowingNormalization) () -> normalizer.normalizeNickname("ㄱ")),
                Arguments.of("emoji whitespace", (ThrowingNormalization) () -> normalizer.normalizeEmoji(" ")),
                Arguments.of("plain digit 1", (ThrowingNormalization) () -> normalizer.normalizeEmoji("1")),
                Arguments.of("multiple emoji", (ThrowingNormalization) () -> normalizer.normalizeEmoji("😀😀"))
        );
    }

    @Test
    @DisplayName("emoji null과 정확한 empty string은 null로 정규화한다")
    void givenAbsentEmojiValues_whenNormalize_thenReturnsNull() {
        assertThat(normalizer.normalizeEmoji(null)).isNull();
        assertThat(normalizer.normalizeEmoji("")).isNull();
    }

    @FunctionalInterface
    private interface ThrowingNormalization {

        String normalize();
    }
}
