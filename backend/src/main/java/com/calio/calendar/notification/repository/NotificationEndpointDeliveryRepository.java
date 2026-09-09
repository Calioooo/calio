package com.calio.calendar.notification.repository;

import com.calio.calendar.notification.domain.NotificationEndpointDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEndpointDeliveryRepository extends JpaRepository<NotificationEndpointDelivery, Long> {
}
