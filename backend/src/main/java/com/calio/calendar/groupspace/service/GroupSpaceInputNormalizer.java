package com.calio.calendar.groupspace.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class GroupSpaceInputNormalizer {

    private static final Normalizer2 NFC = Normalizer2.getNFCInstance();
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[A-Za-z0-9가-힣]{1,9}$");
    private static final UnicodeSet RGI_EMOJI = new UnicodeSet("[:RGI_Emoji:]").freeze();

    public String normalizeName(String name) {
        if (name == null) {
            throw validationFailed();
        }

        String normalized = NFC.normalize(name.trim());
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 30) {
            throw validationFailed();
        }
        if (normalized.codePoints().anyMatch(GroupSpaceInputNormalizer::isControl)) {
            throw validationFailed();
        }
        return normalized;
    }

    public String normalizeNickname(String nickname) {
        if (nickname == null) {
            throw validationFailed();
        }

        String normalized = NFC.normalize(nickname);
        if (!NICKNAME_PATTERN.matcher(normalized).matches()) {
            throw validationFailed();
        }
        return normalized;
    }

    public String normalizeEmoji(String emoji) {
        if (emoji == null || emoji.isEmpty()) {
            return null;
        }
        if (emoji.isBlank()) {
            throw validationFailed();
        }

        String normalized = NFC.normalize(emoji);
        validateEmojiCodePoints(normalized);
        if (!isSingleGrapheme(normalized) || !containsEmoji(normalized)) {
            throw validationFailed();
        }
        return normalized;
    }

    private void validateEmojiCodePoints(String emoji) {
        int codePointCount = emoji.codePointCount(0, emoji.length());
        if (codePointCount == 0 || codePointCount > 32 || containsUnpairedSurrogate(emoji)) {
            throw validationFailed();
        }
        if (emoji.codePoints().anyMatch(GroupSpaceInputNormalizer::isControl)) {
            throw validationFailed();
        }
    }

    private boolean isSingleGrapheme(String emoji) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(ULocale.ROOT);
        iterator.setText(emoji);
        if (iterator.first() != 0 || iterator.next() != emoji.length()) {
            return false;
        }
        return iterator.next() == BreakIterator.DONE;
    }

    private boolean containsEmoji(String emoji) {
        if (RGI_EMOJI.contains(emoji)) {
            return true;
        }
        return emoji.codePoints().anyMatch(codePoint ->
                UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI_PRESENTATION)
                        || UCharacter.hasBinaryProperty(codePoint, UProperty.EXTENDED_PICTOGRAPHIC)
        );
    }

    private static boolean containsUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++index))) {
                    return true;
                }
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isControl(int codePoint) {
        return UCharacter.getType(codePoint) == Character.CONTROL;
    }

    private static CalioException validationFailed() {
        return new CalioException(ErrorCode.VALIDATION_FAILED);
    }
}
