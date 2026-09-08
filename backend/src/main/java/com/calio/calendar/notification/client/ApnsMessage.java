package com.calio.calendar.notification.client;

import java.time.Instant;

public record ApnsMessage(String token, String payload, Instant expiration) { }
