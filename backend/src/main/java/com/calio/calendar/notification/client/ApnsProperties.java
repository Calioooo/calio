package com.calio.calendar.notification.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notifications.apns")
public record ApnsProperties(String environment, String teamId, String keyId, String bundleId, String privateKey) {

    public ApnsProperties {
        environment = isBlank(environment) ? "development" : environment;
    }

    public boolean configured() {
        return !isBlank(teamId) && !isBlank(keyId) && !isBlank(bundleId) && !isBlank(privateKey);
    }

    public String host() {
        return "production".equalsIgnoreCase(environment)
                ? "https://api.push.apple.com"
                : "https://api.sandbox.push.apple.com";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
