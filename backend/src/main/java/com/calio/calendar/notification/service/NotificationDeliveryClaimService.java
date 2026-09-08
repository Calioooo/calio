package com.calio.calendar.notification.service;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.domain.NotificationDelivery;
import com.calio.calendar.notification.repository.NotificationDeliveryRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryClaimService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final AccountQueryService accountQueryService;

    public NotificationDeliveryClaimService(
            NotificationDeliveryRepository deliveryRepository,
            AccountQueryService accountQueryService
    ) {
        this.deliveryRepository = deliveryRepository;
        this.accountQueryService = accountQueryService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationDelivery claim(
            Long accountId,
            String type,
            String key,
            Instant scheduledAt,
            LocalDate targetDate,
            String title,
            String groupName
    ) {
        return deliveryRepository.saveAndFlush(new NotificationDelivery(
                accountQueryService.getAccount(accountId),
                type,
                key,
                scheduledAt,
                targetDate,
                title,
                groupName
        ));
    }
}
