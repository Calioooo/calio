package com.calio.calendar.integration.googlecalendar.config;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.token-encryption")
public class TokenEncryptionProperties {

    private static final int AES_256_KEY_BYTES = 32;

    private String googleRefreshTokenKey = "";

    public String getGoogleRefreshTokenKey() {
        return googleRefreshTokenKey;
    }

    public void setGoogleRefreshTokenKey(String googleRefreshTokenKey) {
        this.googleRefreshTokenKey = googleRefreshTokenKey;
    }

    public boolean hasValidGoogleRefreshTokenKey() {
        if (googleRefreshTokenKey == null || googleRefreshTokenKey.isBlank()) {
            return false;
        }

        try {
            return Base64.getDecoder().decode(googleRefreshTokenKey).length == AES_256_KEY_BYTES;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
