package com.calio.calendar.security;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.encrypt.AesBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;

@Configuration
public class TokenEncryptionConfig {

    private static final String GOOGLE_TOKEN_BYTES_ENCRYPTOR = "googleTokenBytesEncryptor";
    private static final String AES_ALGORITHM = "AES";
    private static final int AES_256_KEY_BYTES = 32;

    @Bean(GOOGLE_TOKEN_BYTES_ENCRYPTOR)
    @Lazy
    public BytesEncryptor googleTokenBytesEncryptor(TokenEncryptionProperties properties) {
        return new AesBytesEncryptor(
                new SecretKeySpec(encryptionKey(properties), AES_ALGORITHM),
                AesBytesEncryptor.CipherAlgorithm.GCM.defaultIvGenerator(),
                AesBytesEncryptor.CipherAlgorithm.GCM
        );
    }

    private byte[] encryptionKey(TokenEncryptionProperties properties) {
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
}
