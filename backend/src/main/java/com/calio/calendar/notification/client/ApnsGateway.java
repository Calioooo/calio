package com.calio.calendar.notification.client;

public interface ApnsGateway {
    ApnsSendResult send(ApnsMessage message);
}
