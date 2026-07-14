package com.calio.calendar.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.googlecalendar.config.TokenEncryptionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoogleRefreshTokenEncryptorTest {

    @Test
    @DisplayName("refresh token은 v1 envelope로 암호화하고 다시 복호화할 수 있다")
    void givenRefreshToken_whenEncryptAndDecrypt_thenReturnsOriginalToken() {
        // given
        GoogleRefreshTokenEncryptor encryptor = new GoogleRefreshTokenEncryptor(validProperties());

        // when
        String envelope = encryptor.encrypt("refresh-token-plain");

        // then
        assertThat(envelope).startsWith("v1:1:");
        assertThat(envelope).doesNotContain("refresh-token-plain");
        assertThat(encryptor.decrypt(envelope)).isEqualTo("refresh-token-plain");
    }

    @Test
    @DisplayName("같은 refresh token도 매번 다른 nonce로 다른 envelope를 생성한다")
    void givenSameRefreshToken_whenEncryptTwice_thenReturnsDifferentEnvelopes() {
        // given
        GoogleRefreshTokenEncryptor encryptor = new GoogleRefreshTokenEncryptor(validProperties());

        // when
        String firstEnvelope = encryptor.encrypt("same-refresh-token");
        String secondEnvelope = encryptor.encrypt("same-refresh-token");

        // then
        assertThat(firstEnvelope).isNotEqualTo(secondEnvelope);
        assertThat(encryptor.decrypt(firstEnvelope)).isEqualTo("same-refresh-token");
        assertThat(encryptor.decrypt(secondEnvelope)).isEqualTo("same-refresh-token");
    }

    @Test
    @DisplayName("잘못된 envelope는 GOOGLE_REFRESH_TOKEN_ENCRYPTION_FAILED로 실패한다")
    void givenInvalidEnvelope_whenDecrypt_thenThrowsEncryptionFailed() {
        // given
        GoogleRefreshTokenEncryptor encryptor = new GoogleRefreshTokenEncryptor(validProperties());

        // when then
        assertThatThrownBy(() -> encryptor.decrypt("invalid-envelope"))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_REFRESH_TOKEN_ENCRYPTION_FAILED));
    }

    private TokenEncryptionProperties validProperties() {
        TokenEncryptionProperties properties = new TokenEncryptionProperties();
        properties.setGoogleRefreshTokenKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        return properties;
    }
}
