package com.calio.calendar.external.google;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "external.google.oauth")
public class GoogleOAuthProperties {

    private String tokenUrl = "https://oauth2.googleapis.com/token";
    private String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
    private String revokeUrl = "https://oauth2.googleapis.com/revoke";
    private String clientId;
    private String clientSecret;
    private String redirectUri;

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

    public String getRevokeUrl() {
        return revokeUrl;
    }

    public void setRevokeUrl(String revokeUrl) {
        this.revokeUrl = revokeUrl;
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

    public boolean isConfigured() {
        return hasText(tokenUrl)
                && hasText(userInfoUrl)
                && hasText(revokeUrl)
                && hasText(clientId)
                && hasText(clientSecret)
                && hasText(redirectUri);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
