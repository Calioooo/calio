package com.calio.calendar.security;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.stereotype.Component;

@Component
public class TokenEncryptor {

    private static final Logger log = LoggerFactory.getLogger(TokenEncryptor.class);
    private static final String ENVELOPE_VERSION = "v1";
    private static final String KEY_VERSION = "google-refresh-token-key:v1";
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BYTES = 16;

    private final BytesEncryptor bytesEncryptor;

    public TokenEncryptor(
            @Qualifier("googleTokenBytesEncryptor") BytesEncryptor bytesEncryptor
    ) {
        this.bytesEncryptor = bytesEncryptor;
    }

    public String encryptRefreshToken(String plaintext) {
        return encrypt(plaintext, GoogleTokenType.REFRESH_TOKEN);
    }

    public String encryptAccessToken(String plaintext) {
        return encrypt(plaintext, GoogleTokenType.ACCESS_TOKEN);
    }

    String encrypt(String plaintext) {
        return encrypt(plaintext, GoogleTokenType.UNKNOWN_TOKEN);
    }

    private String encrypt(String plaintext, GoogleTokenType tokenType) {
        try {
            byte[] encrypted = bytesEncryptor.encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
            return TokenEnvelope.fromEncryptedBytes(KEY_VERSION, encrypted).serialize();
        } catch (CalioException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                    "Google token encryption failed. tokenType={} causeType={}",
                    tokenType,
                    exception.getClass().getSimpleName()
            );
            throw new CalioException(ErrorCode.GOOGLE_TOKEN_ENCRYPTION_FAILED, exception);
        }
    }

    public String decrypt(String envelopeValue) {
        try {
            TokenEnvelope envelope = TokenEnvelope.parse(envelopeValue);
            byte[] plaintext = bytesEncryptor.decrypt(envelope.encryptedBytes());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (CalioException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn(
                    "Google token decryption failed. tokenType={} causeType={}",
                    GoogleTokenType.UNKNOWN_TOKEN,
                    exception.getClass().getSimpleName()
            );
            throw new CalioException(ErrorCode.GOOGLE_TOKEN_ENCRYPTION_FAILED, exception);
        }
    }

    private enum GoogleTokenType {
        REFRESH_TOKEN,
        ACCESS_TOKEN,
        UNKNOWN_TOKEN
    }

    private record TokenEnvelope(
            String keyVersion,
            byte[] nonce,
            byte[] ciphertext,
            byte[] authenticationTag
    ) {

        private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

        static TokenEnvelope fromEncryptedBytes(String keyVersion, byte[] encrypted) {
            if (encrypted.length < GCM_NONCE_BYTES + GCM_TAG_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted token");
            }

            byte[] nonce = Arrays.copyOfRange(encrypted, 0, GCM_NONCE_BYTES);
            int authenticationTagOffset = encrypted.length - GCM_TAG_BYTES;
            byte[] ciphertext = Arrays.copyOfRange(encrypted, GCM_NONCE_BYTES, authenticationTagOffset);
            byte[] authenticationTag = Arrays.copyOfRange(encrypted, authenticationTagOffset, encrypted.length);
            return new TokenEnvelope(keyVersion, nonce, ciphertext, authenticationTag);
        }

        static TokenEnvelope parse(String value) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 5 || !ENVELOPE_VERSION.equals(parts[0]) || !KEY_VERSION.equals(parts[1])) {
                throw new IllegalArgumentException("Unsupported encrypted token envelope");
            }

            return new TokenEnvelope(
                    parts[1],
                    DECODER.decode(parts[2]),
                    DECODER.decode(parts[3]),
                    DECODER.decode(parts[4])
            );
        }

        String serialize() {
            return String.join(
                    ".",
                    ENVELOPE_VERSION,
                    keyVersion,
                    ENCODER.encodeToString(nonce),
                    ENCODER.encodeToString(ciphertext),
                    ENCODER.encodeToString(authenticationTag)
            );
        }

        byte[] encryptedBytes() {
            return ByteBuffer.allocate(nonce.length + ciphertext.length + authenticationTag.length)
                    .put(nonce)
                    .put(ciphertext)
                    .put(authenticationTag)
                    .array();
        }
    }
}
