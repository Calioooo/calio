package com.calio.calendar.notification.apns;

public interface ApnsGateway {
    ApnsSendResult send(ApnsMessage message);
}
