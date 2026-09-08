package com.calio.calendar.notification;

import com.calio.calendar.notification.client.ApnsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ApnsProperties.class)
public class NotificationConfiguration {
}
