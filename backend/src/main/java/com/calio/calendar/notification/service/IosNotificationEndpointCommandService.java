package com.calio.calendar.notification.service;

import com.calio.calendar.notification.domain.IosNotificationEndpoint;
import com.calio.calendar.notification.repository.IosNotificationEndpointRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IosNotificationEndpointCommandService {

    private final IosNotificationEndpointRepository endpointRepository;

    public IosNotificationEndpointCommandService(IosNotificationEndpointRepository endpointRepository) {
        this.endpointRepository = endpointRepository;
    }

    public IosNotificationEndpoint create(IosNotificationEndpoint endpoint) {
        return endpointRepository.save(endpoint);
    }

    public void deactivateAndReleaseToken(IosNotificationEndpoint endpoint, Instant now) {
        endpoint.deactivate(now);
        endpoint.clearApnsToken();
        endpointRepository.saveAndFlush(endpoint);
    }
}
