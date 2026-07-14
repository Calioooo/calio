package com.calio.calendar.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenEncryptorTest {

    private final TokenEncryptionProperties properties = tokenEncryptionProperties();
    private final TokenEncryptor tokenEncryptor = new TokenEncryptor(properties);

    @Test
    @DisplayName("TokenEncryptor는 AES-GCM envelope를 복호화 가능한 형태로 저장한다")
    void givenPlaintextToken_whenEncryptAndDecrypt_thenRestoresOriginalToken() {
        // given
        String plaintext = "refresh-token-value";

        // when
        String encrypted = tokenEncryptor.encrypt(plaintext);

        // then
        assertThat(encrypted).startsWith("v1.google-refresh-token-key:v1.");
        assertThat(tokenEncryptor.decrypt(encrypted)).isEqualTo(plaintext);
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

    private TokenEncryptionProperties tokenEncryptionProperties() {
        TokenEncryptionProperties encryptionProperties = new TokenEncryptionProperties();
        encryptionProperties.setGoogleRefreshTokenKey("12345678901234567890123456789012");
        return encryptionProperties;
    }
}
