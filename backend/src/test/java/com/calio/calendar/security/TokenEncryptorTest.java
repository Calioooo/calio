package com.calio.calendar.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

class TokenEncryptorTest {

    private static final String LEGACY_V1_ENVELOPE =
            "v1.google-refresh-token-key:v1.AAECAwQFBgcICQoL.NDEAexZytxjD5kNW4ZdbNjexeFE.IwB_5RbhurxZJYvaiEGxsQ";

    private final TokenEncryptionProperties properties = tokenEncryptionProperties();
    private final BytesEncryptor bytesEncryptor = new TokenEncryptionConfig().googleTokenBytesEncryptor(properties);
    private final TokenEncryptor tokenEncryptor = new TokenEncryptor(bytesEncryptor);

    @Test
    @DisplayName("TokenEncryptor는 AES-GCM envelope를 복호화 가능한 형태로 저장한다")
    void givenPlaintextToken_whenEncryptAndDecrypt_thenRestoresOriginalToken() {
        // given
        String plaintext = "refresh-token-value";

        // when
        String encrypted = tokenEncryptor.encrypt(plaintext);

        // then
        assertThat(encrypted).startsWith("v1.google-refresh-token-key:v1.");
        assertThat(Base64.getUrlDecoder().decode(encrypted.split("\\.")[2])).hasSize(12);
        assertThat(tokenEncryptor.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("TokenEncryptor는 기존 12바이트 nonce의 v1 envelope를 복호화한다")
    void givenLegacyV1Envelope_whenDecrypt_thenRestoresOriginalToken() {
        // when
        String decrypted = tokenEncryptor.decrypt(LEGACY_V1_ENVELOPE);

        // then
        assertThat(decrypted).isEqualTo("legacy-refresh-token");
    }

    @Test
    @DisplayName("TokenEncryptor는 같은 token을 암호화해도 매번 다른 nonce envelope를 만든다")
    void givenSamePlaintextToken_whenEncryptTwice_thenCreatesDifferentEnvelope() {
        // given
        String plaintext = "access-token-value";

        // when
        String first = tokenEncryptor.encrypt(plaintext);
        String second = tokenEncryptor.encrypt(plaintext);

        // then
        assertThat(first).isNotEqualTo(second);
        assertThat(tokenEncryptor.decrypt(first)).isEqualTo(plaintext);
        assertThat(tokenEncryptor.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Token encryption key가 32바이트가 아니면 encryptor 생성을 거부한다")
    void givenInvalidEncryptionKey_whenCreateEncryptor_thenRejectsConfiguration() {
        // given
        TokenEncryptionProperties invalidProperties = new TokenEncryptionProperties();
        invalidProperties.setGoogleRefreshTokenKey("short-key");

        // when, then
        assertThatThrownBy(() -> new TokenEncryptionConfig().googleTokenBytesEncryptor(invalidProperties))
                .isInstanceOfSatisfying(CalioException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.GOOGLE_CALENDAR_CONFIGURATION_MISSING));
    }

    private TokenEncryptionProperties tokenEncryptionProperties() {
        TokenEncryptionProperties encryptionProperties = new TokenEncryptionProperties();
        encryptionProperties.setGoogleRefreshTokenKey("12345678901234567890123456789012");
        return encryptionProperties;
    }
}
