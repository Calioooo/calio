package com.calio.calendar.notification.service;

import com.calio.calendar.notification.repository.NotificationDeliveryRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationDeliveryQueryService {

    private final NotificationDeliveryRepository deliveryRepository;

    public NotificationDeliveryQueryService(NotificationDeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    public boolean hasDeliveryClaim(Long accountId, String type, String key, Instant scheduledAt) {
        return deliveryRepository.findByAccount_IdAndNotificationTypeAndScheduleKeyAndScheduledAt(
                accountId,
                type,
                key,
                scheduledAt
        ).isPresent();
    }
}
