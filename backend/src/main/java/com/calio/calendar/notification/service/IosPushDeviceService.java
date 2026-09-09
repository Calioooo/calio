package com.calio.calendar.notification.service;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.notification.client.ApnsProperties;
import com.calio.calendar.notification.domain.IosPushDevice;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IosPushDeviceService {

    private final AccountQueryService accountQueryService;
    private final ApnsProperties apnsProperties;
    private final IosPushDeviceQueryService pushDeviceQueryService;
    private final IosPushDeviceCommandService pushDeviceCommandService;

    public IosPushDeviceService(
            AccountQueryService accountQueryService,
            ApnsProperties apnsProperties,
            IosPushDeviceQueryService pushDeviceQueryService,
            IosPushDeviceCommandService pushDeviceCommandService
    ) {
        this.accountQueryService = accountQueryService;
        this.apnsProperties = apnsProperties;
        this.pushDeviceQueryService = pushDeviceQueryService;
        this.pushDeviceCommandService = pushDeviceCommandService;
    }

    public void register(
            Long accountId,
            String installationId,
            String apnsToken
    ) {
        deactivatePushDeviceOwnedByAnotherInstallation(accountId, installationId, apnsToken);

        IosPushDevice pushDevice = pushDeviceQueryService
                .getPushDeviceIfExists(accountId, installationId)
                .orElseGet(() -> new IosPushDevice(
                        accountQueryService.getAccount(accountId),
                        installationId,
                        apnsToken,
                        apnsProperties.environment()
                ));
        refreshPushDevice(pushDevice, apnsToken);
    }

    public void deactivate(Long accountId, String installationId) {
        pushDeviceQueryService.getPushDeviceIfExists(accountId, installationId)
                .ifPresent(pushDevice -> pushDevice.deactivate(Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<IosPushDevice> listEligiblePushDevices(Long accountId) {
        return pushDeviceQueryService.listEligiblePushDevices(accountId);
    }

    public void deactivateInvalidPushDevice(IosPushDevice pushDevice) {
        pushDevice.deactivate(Instant.now());
    }

    private void deactivatePushDeviceOwnedByAnotherInstallation(
            Long accountId,
            String installationId,
            String apnsToken
    ) {
        pushDeviceQueryService.getPushDeviceWithTokenIfExists(apnsToken)
                .filter(pushDevice -> !isSameInstallation(pushDevice, accountId, installationId))
                .ifPresent(pushDevice -> deactivateAndClearToken(pushDevice, Instant.now()));
    }

    private void deactivateAndClearToken(IosPushDevice pushDevice, Instant now) {
        pushDeviceCommandService.deactivateAndReleaseToken(pushDevice, now);
    }

    private void refreshPushDevice(
            IosPushDevice pushDevice,
            String apnsToken
    ) {
        pushDevice.refresh(apnsToken, apnsProperties.environment());
        try {
            if (pushDevice.getId() == null) {
                pushDeviceCommandService.create(pushDevice);
                return;
            }
            pushDeviceCommandService.change(pushDevice);
        } catch (DataIntegrityViolationException exception) {
            throw new CalioException(ErrorCode.NOTIFICATION_ENDPOINT_TOKEN_CONFLICT, exception);
        }
    }

    private boolean isSameInstallation(
            IosPushDevice pushDevice,
            Long accountId,
            String installationId
    ) {
        return pushDeviceQueryService.getPushDeviceIfExists(accountId, installationId)
                .map(currentPushDevice -> currentPushDevice.getId().equals(pushDevice.getId()))
                .orElse(false);
    }
}
