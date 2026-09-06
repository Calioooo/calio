package com.calio.calendar.external.google;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "external.google.oauth")
public class GoogleOAuthProperties {

    private String tokenUrl = "https://oauth2.googleapis.com/token";
    private String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
    private String revokeUrl = "https://oauth2.googleapis.com/revoke";
    private String calendarEventsUrl =
            "https://www.googleapis.com/calendar/v3/calendars/primary/events";
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

    public String getCalendarEventsUrl() {
        return calendarEventsUrl;
    }

    public void setCalendarEventsUrl(String calendarEventsUrl) {
        requireHttpsCalendarEventsUrl(calendarEventsUrl);
        this.calendarEventsUrl = calendarEventsUrl;
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
                && hasText(calendarEventsUrl)
                && hasText(clientId)
                && hasText(clientSecret)
                && hasText(redirectUri);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void requireHttpsCalendarEventsUrl(String calendarEventsUrl) {
        if (!hasText(calendarEventsUrl)) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(calendarEventsUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Google Calendar Events URL must be a valid HTTPS URL", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Google Calendar Events URL must use HTTPS");
        }
    }
}
