package com.calio.calendar.notification.service;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.notification.client.ApnsProperties;
import com.calio.calendar.notification.domain.IosNotificationAuthorizationStatus;
import com.calio.calendar.notification.domain.IosNotificationEndpoint;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IosNotificationEndpointService {

    private final AccountQueryService accountQueryService;
    private final ApnsProperties apnsProperties;
    private final IosNotificationEndpointQueryService endpointQueryService;
    private final IosNotificationEndpointCommandService endpointCommandService;

    public IosNotificationEndpointService(
            AccountQueryService accountQueryService,
            ApnsProperties apnsProperties,
            IosNotificationEndpointQueryService endpointQueryService,
            IosNotificationEndpointCommandService endpointCommandService
    ) {
        this.accountQueryService = accountQueryService;
        this.apnsProperties = apnsProperties;
        this.endpointQueryService = endpointQueryService;
        this.endpointCommandService = endpointCommandService;
    }

    public void register(
            Long accountId,
            String installationId,
            String apnsToken,
            IosNotificationAuthorizationStatus authorizationStatus
    ) {
        deactivateEndpointOwnedByAnotherInstallation(accountId, installationId, apnsToken);

        IosNotificationEndpoint endpoint = endpointQueryService
                .getEndpointIfExists(accountId, installationId)
                .orElseGet(() -> new IosNotificationEndpoint(
                        accountQueryService.getAccount(accountId),
                        installationId,
                        apnsToken,
                        authorizationStatus,
                        apnsProperties.environment()
                ));
        refreshEndpoint(endpoint, apnsToken, authorizationStatus);
    }

    public void deactivate(Long accountId, String installationId) {
        endpointQueryService.getEndpointIfExists(accountId, installationId)
                .ifPresent(endpoint -> endpoint.deactivate(Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<IosNotificationEndpoint> listEligibleEndpoints(Long accountId) {
        return endpointQueryService.listEligibleEndpoints(accountId);
    }

    public void deactivateInvalidEndpoint(IosNotificationEndpoint endpoint) {
        endpoint.deactivate(Instant.now());
    }

    private void deactivateEndpointOwnedByAnotherInstallation(
            Long accountId,
            String installationId,
            String apnsToken
    ) {
        endpointQueryService.getEndpointWithTokenIfExists(apnsToken)
                .filter(endpoint -> !isSameInstallation(endpoint, accountId, installationId))
                .ifPresent(endpoint -> deactivateAndClearToken(endpoint, Instant.now()));
    }

    private void deactivateAndClearToken(IosNotificationEndpoint endpoint, Instant now) {
        endpointCommandService.deactivateAndReleaseToken(endpoint, now);
    }

    private void refreshEndpoint(
            IosNotificationEndpoint endpoint,
            String apnsToken,
            IosNotificationAuthorizationStatus authorizationStatus
    ) {
        endpoint.refresh(apnsToken, authorizationStatus, apnsProperties.environment());
        try {
            if (endpoint.getId() == null) {
                endpointCommandService.create(endpoint);
                return;
            }
            endpointCommandService.change(endpoint);
        } catch (DataIntegrityViolationException exception) {
            throw new CalioException(ErrorCode.NOTIFICATION_ENDPOINT_TOKEN_CONFLICT, exception);
        }
    }

    private boolean isSameInstallation(
            IosNotificationEndpoint endpoint,
            Long accountId,
            String installationId
    ) {
        return endpointQueryService.getEndpointIfExists(accountId, installationId)
                .map(currentEndpoint -> currentEndpoint.getId().equals(endpoint.getId()))
                .orElse(false);
    }
}
