package com.calio.calendar.notification.client;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notifications.apns")
public record ApnsProperties(
    ApnsEnvironment environment, String teamId, String keyId, String bundleId, String privateKey) {

  public ApnsProperties {
    Objects.requireNonNull(environment, "APNs environment must be configured.");
  }

  public boolean configured() {
    return !isBlank(teamId) && !isBlank(keyId) && !isBlank(bundleId) && !isBlank(privateKey);
  }

  public String host() {
    return environment.host();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
