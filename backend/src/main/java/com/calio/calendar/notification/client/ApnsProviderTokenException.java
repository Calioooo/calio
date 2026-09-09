package com.calio.calendar.notification.client;

public class ApnsProviderTokenException extends RuntimeException {

    public ApnsProviderTokenException(String message) {
        super(message);
    }

    public ApnsProviderTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
