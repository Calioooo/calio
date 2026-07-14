package com.calio.calendar.security;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.integration.googlecalendar.config.TokenEncryptionProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GoogleRefreshTokenEncryptor {

    private static final String ENVELOPE_VERSION = "v1";
    private static final String KEY_VERSION = "1";
    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_TAG_BYTES = 16;
    private static final int NONCE_BYTES = 12;

    private final TokenEncryptionProperties properties;
    private final SecureRandom secureRandom;

    @Autowired
    public GoogleRefreshTokenEncryptor(TokenEncryptionProperties properties) {
        this(properties, new SecureRandom());
    }

    GoogleRefreshTokenEncryptor(TokenEncryptionProperties properties, SecureRandom secureRandom) {
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    public String encrypt(String refreshToken) {
        try {
            byte[] nonce = randomNonce();
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, nonce));

            byte[] encrypted = cipher.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = Arrays.copyOf(encrypted, encrypted.length - GCM_TAG_BYTES);
            byte[] tag = Arrays.copyOfRange(encrypted, encrypted.length - GCM_TAG_BYTES, encrypted.length);
            return envelope(nonce, ciphertext, tag);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new CalioException(ErrorCode.GOOGLE_REFRESH_TOKEN_ENCRYPTION_FAILED, exception);
        }
    }

    public String decrypt(String envelope) {
        try {
            EnvelopeParts parts = EnvelopeParts.parse(envelope);
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, parts.nonce()));

            byte[] encrypted = concat(parts.ciphertext(), parts.tag());
            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new CalioException(ErrorCode.GOOGLE_REFRESH_TOKEN_ENCRYPTION_FAILED, exception);
        }
    }

    private byte[] randomNonce() {
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        return nonce;
    }

    private SecretKeySpec keySpec() {
        byte[] key = Base64.getDecoder().decode(properties.getGoogleRefreshTokenKey());
        return new SecretKeySpec(key, AES_ALGORITHM);
    }

    private String envelope(byte[] nonce, byte[] ciphertext, byte[] tag) {
        Base64.Encoder encoder = Base64.getEncoder();
        return String.join(
                ":",
                ENVELOPE_VERSION,
                KEY_VERSION,
                encoder.encodeToString(nonce),
                encoder.encodeToString(ciphertext),
                encoder.encodeToString(tag)
        );
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] combined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private record EnvelopeParts(byte[] nonce, byte[] ciphertext, byte[] tag) {

        private static EnvelopeParts parse(String envelope) {
            String[] parts = envelope.split(":", -1);
            if (parts.length != 5 || !ENVELOPE_VERSION.equals(parts[0]) || !KEY_VERSION.equals(parts[1])) {
                throw new IllegalArgumentException("Invalid Google refresh token envelope");
            }

            Base64.Decoder decoder = Base64.getDecoder();
            return new EnvelopeParts(
                    decoder.decode(parts[2]),
                    decoder.decode(parts[3]),
                    decoder.decode(parts[4])
            );
        }
    }
}
