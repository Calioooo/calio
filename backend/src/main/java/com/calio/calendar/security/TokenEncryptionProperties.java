package com.calio.calendar.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.token-encryption")
public class TokenEncryptionProperties {

    private String googleRefreshTokenKey;

    public String getGoogleRefreshTokenKey() {
        return googleRefreshTokenKey;
    }

    public void setGoogleRefreshTokenKey(String googleRefreshTokenKey) {
        this.googleRefreshTokenKey = googleRefreshTokenKey;
    }

    public boolean hasGoogleRefreshTokenKey() {
        return googleRefreshTokenKey != null && !googleRefreshTokenKey.isBlank();
    }
}
