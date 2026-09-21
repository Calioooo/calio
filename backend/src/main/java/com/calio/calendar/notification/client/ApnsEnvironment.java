package com.calio.calendar.notification.client;

public enum ApnsEnvironment {
  DEVELOPMENT("development", "https://api.sandbox.push.apple.com"),
  PRODUCTION("production", "https://api.push.apple.com");

  private final String value;
  private final String host;

  ApnsEnvironment(String value, String host) {
    this.value = value;
    this.host = host;
  }

  public String value() {
    return value;
  }

  public String host() {
    return host;
  }
}
