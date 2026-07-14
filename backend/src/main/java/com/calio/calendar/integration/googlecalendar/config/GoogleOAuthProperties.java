package com.calio.calendar.integration.googlecalendar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "external.google.oauth")
public class GoogleOAuthProperties {

    private static final String DEFAULT_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String DEFAULT_USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private String tokenUrl = DEFAULT_TOKEN_URL;
    private String userInfoUrl = DEFAULT_USER_INFO_URL;
    private String clientId = "";
    private String clientSecret = "";
    private String redirectUri = "";

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getUserInfoUrl() {
        return userInfoUrl;
    }

    public void setUserInfoUrl(String userInfoUrl) {
        this.userInfoUrl = userInfoUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public boolean hasRequiredSettings() {
        return hasText(tokenUrl)
                && hasText(userInfoUrl)
                && hasText(clientId)
                && hasText(clientSecret)
                && hasText(redirectUri);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
