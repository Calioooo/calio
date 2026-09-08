package com.calio.calendar.notification.service;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.notification.apns.ApnsProperties;
import com.calio.calendar.notification.domain.IosNotificationAuthorizationStatus;
import com.calio.calendar.notification.domain.IosNotificationEndpoint;
import com.calio.calendar.notification.repository.IosNotificationEndpointRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IosNotificationEndpointService {

    private final IosNotificationEndpointRepository endpointRepository;
    private final AccountQueryService accountQueryService;
    private final ApnsProperties apnsProperties;

    public IosNotificationEndpointService(
            IosNotificationEndpointRepository endpointRepository,
            AccountQueryService accountQueryService,
            ApnsProperties apnsProperties
    ) {
        this.endpointRepository = endpointRepository;
        this.accountQueryService = accountQueryService;
        this.apnsProperties = apnsProperties;
    }

    public void register(
            Long accountId,
            String installationId,
            String apnsToken,
            IosNotificationAuthorizationStatus authorizationStatus
    ) {
        deactivateEndpointOwnedByAnotherInstallation(accountId, installationId, apnsToken);

        IosNotificationEndpoint endpoint = endpointRepository
                .findByAccount_IdAndInstallationId(accountId, installationId)
                .orElseGet(() -> new IosNotificationEndpoint(
                        accountQueryService.getAccount(accountId),
                        installationId,
                        apnsToken,
                        authorizationStatus,
                        apnsProperties.environment()
                ));
        endpoint.refresh(apnsToken, authorizationStatus, apnsProperties.environment());
        endpointRepository.save(endpoint);
    }

    public void deactivate(Long accountId, String installationId) {
        endpointRepository.findByAccount_IdAndInstallationId(accountId, installationId)
                .ifPresent(endpoint -> endpoint.deactivate(Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<IosNotificationEndpoint> listEligibleEndpoints(Long accountId) {
        return endpointRepository.findByAccount_IdAndActiveTrueAndAuthorizationStatus(
                accountId,
                IosNotificationAuthorizationStatus.AUTHORIZED
        );
    }

    public void deactivateInvalidEndpoint(IosNotificationEndpoint endpoint) {
        endpoint.deactivate(Instant.now());
    }

    private void deactivateEndpointOwnedByAnotherInstallation(
            Long accountId,
            String installationId,
            String apnsToken
    ) {
        endpointRepository.findByApnsToken(apnsToken)
                .filter(endpoint -> !isSameInstallation(endpoint, accountId, installationId))
                .ifPresent(endpoint -> deactivateAndClearToken(endpoint, Instant.now()));
    }

    private void deactivateAndClearToken(IosNotificationEndpoint endpoint, Instant now) {
        endpoint.deactivate(now);
        endpoint.clearApnsToken();
    }

    private boolean isSameInstallation(
            IosNotificationEndpoint endpoint,
            Long accountId,
            String installationId
    ) {
        return endpointRepository.findByAccount_IdAndInstallationId(accountId, installationId)
                .map(currentEndpoint -> currentEndpoint.getId().equals(endpoint.getId()))
                .orElse(false);
    }
}
