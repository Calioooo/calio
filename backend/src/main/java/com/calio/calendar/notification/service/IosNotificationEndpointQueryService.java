package com.calio.calendar.notification.service;

import com.calio.calendar.notification.domain.IosNotificationAuthorizationStatus;
import com.calio.calendar.notification.domain.IosNotificationEndpoint;
import com.calio.calendar.notification.repository.IosNotificationEndpointRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IosNotificationEndpointQueryService {

    private final IosNotificationEndpointRepository endpointRepository;

    public IosNotificationEndpointQueryService(IosNotificationEndpointRepository endpointRepository) {
        this.endpointRepository = endpointRepository;
    }

    public Optional<IosNotificationEndpoint> getEndpointIfExists(Long accountId, String installationId) {
        return endpointRepository.findByAccount_IdAndInstallationId(accountId, installationId);
    }

    public Optional<IosNotificationEndpoint> getEndpointWithTokenIfExists(String apnsToken) {
        return endpointRepository.lockEndpointWithToken(apnsToken);
    }

    public List<IosNotificationEndpoint> listEligibleEndpoints(Long accountId) {
        return endpointRepository.findByAccount_IdAndActiveTrueAndAuthorizationStatus(
                accountId,
                IosNotificationAuthorizationStatus.AUTHORIZED
        );
    }
}
