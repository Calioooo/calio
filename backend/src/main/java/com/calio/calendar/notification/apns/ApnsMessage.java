package com.calio.calendar.notification.apns;

import java.time.Instant;

public record ApnsMessage(String token, String payload, Instant expiration) { }
