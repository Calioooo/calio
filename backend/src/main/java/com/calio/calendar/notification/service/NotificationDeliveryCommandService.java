package com.calio.calendar.notification.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.notification.domain.NotificationDelivery;
import com.calio.calendar.notification.repository.NotificationDeliveryRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryCommandService {

    private final NotificationDeliveryRepository deliveryRepository;

    public NotificationDeliveryCommandService(NotificationDeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationDelivery create(
            Account account,
            String type,
            String key,
            Instant scheduledAt,
            LocalDate targetDate,
            String title,
            String groupName
    ) {
        return deliveryRepository.saveAndFlush(new NotificationDelivery(
                account,
                type,
                key,
                scheduledAt,
                targetDate,
                title,
                groupName
        ));
    }
}
