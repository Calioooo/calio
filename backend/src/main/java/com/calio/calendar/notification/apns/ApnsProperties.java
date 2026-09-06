package com.calio.calendar.notification.apns;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notifications.apns")
public record ApnsProperties(String environment, String teamId, String keyId, String bundleId, String privateKey) {
    public boolean configured() {
        return nonBlank(teamId) && nonBlank(keyId) && nonBlank(bundleId) && nonBlank(privateKey);
    }
    public String host() { return "production".equalsIgnoreCase(environment) ? "https://api.push.apple.com" : "https://api.sandbox.push.apple.com"; }
    private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }
}
