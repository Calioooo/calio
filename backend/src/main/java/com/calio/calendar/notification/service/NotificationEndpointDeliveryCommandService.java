package com.calio.calendar.notification.service;

import com.calio.calendar.notification.client.ApnsSendResult;
import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.domain.NotificationDelivery;
import com.calio.calendar.notification.domain.NotificationEndpointDelivery;
import com.calio.calendar.notification.repository.NotificationEndpointDeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationEndpointDeliveryCommandService {

    private final NotificationEndpointDeliveryRepository endpointDeliveryRepository;

    public NotificationEndpointDeliveryCommandService(
            NotificationEndpointDeliveryRepository endpointDeliveryRepository
    ) {
        this.endpointDeliveryRepository = endpointDeliveryRepository;
    }

    public void create(
            NotificationDelivery delivery,
            IosPushDevice endpoint,
            ApnsSendResult result
    ) {
        endpointDeliveryRepository.save(new NotificationEndpointDelivery(
                delivery,
                endpoint,
                result.type().name(),
                result.requestId(),
                result.reason()
        ));
    }
}
