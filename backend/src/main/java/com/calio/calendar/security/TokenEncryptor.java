package com.calio.calendar.security;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class TokenEncryptor {

    private static final String ENVELOPE_VERSION = "v1";
    private static final String KEY_VERSION = "google-refresh-token-key:v1";
    private static final String AES_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_256_KEY_BYTES = 32;
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_TAG_BYTES = GCM_TAG_BITS / Byte.SIZE;

    private final TokenEncryptionProperties properties;
    private final SecureRandom secureRandom;

    public TokenEncryptor(TokenEncryptionProperties properties) {
        this.properties = properties;
        this.secureRandom = new SecureRandom();
    }

    public void validateConfigured() {
        encryptionKey();
    }

    public String encrypt(String plaintext) {
        try {
            byte[] nonce = randomNonce();
            byte[] encrypted = encrypt(plaintext.getBytes(StandardCharsets.UTF_8), nonce);
            return TokenEnvelope.fromEncryptedBytes(KEY_VERSION, nonce, encrypted).serialize();
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new CalioException(ErrorCode.GOOGLE_REFRESH_TOKEN_ENCRYPTION_FAILED, exception);
        }
    }

    public String decrypt(String envelopeValue) {
        try {
            TokenEnvelope envelope = TokenEnvelope.parse(envelopeValue);
            byte[] encrypted = envelope.encryptedBytes();
            byte[] plaintext = decrypt(encrypted, envelope.nonce());
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new CalioException(ErrorCode.GOOGLE_REFRESH_TOKEN_ENCRYPTION_FAILED, exception);
        }
    }

    private byte[] encrypt(byte[] plaintext, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return cipher.doFinal(plaintext);
    }

    private byte[] decrypt(byte[] encrypted, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return cipher.doFinal(encrypted);
    }

    private SecretKeySpec secretKey() {
        return new SecretKeySpec(encryptionKey(), AES_ALGORITHM);
    }

    private byte[] encryptionKey() {
        if (!properties.hasGoogleRefreshTokenKey()) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_CONFIGURATION_MISSING);
        }

        byte[] key = decodeKey(properties.getGoogleRefreshTokenKey());
        if (key.length != AES_256_KEY_BYTES) {
            throw new CalioException(ErrorCode.GOOGLE_CALENDAR_CONFIGURATION_MISSING);
        }
        return key;
    }

    private byte[] decodeKey(String keyValue) {
        byte[] rawKey = keyValue.getBytes(StandardCharsets.UTF_8);
        if (rawKey.length == AES_256_KEY_BYTES) {
            return rawKey;
        }

        try {
            return Base64.getDecoder().decode(keyValue);
        } catch (IllegalArgumentException exception) {
            return rawKey;
        }
    }

    private byte[] randomNonce() {
        byte[] nonce = new byte[GCM_NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        return nonce;
    }

    private record TokenEnvelope(
            String keyVersion,
            byte[] nonce,
            byte[] ciphertext,
            byte[] authenticationTag
    ) {

        private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
        private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

        static TokenEnvelope fromEncryptedBytes(String keyVersion, byte[] nonce, byte[] encrypted) {
            if (encrypted.length <= GCM_TAG_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted token");
            }

            int ciphertextLength = encrypted.length - GCM_TAG_BYTES;
            byte[] ciphertext = Arrays.copyOfRange(encrypted, 0, ciphertextLength);
            byte[] authenticationTag = Arrays.copyOfRange(encrypted, ciphertextLength, encrypted.length);
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
            return ByteBuffer.allocate(ciphertext.length + authenticationTag.length)
                    .put(ciphertext)
                    .put(authenticationTag)
                    .array();
        }
    }
}
