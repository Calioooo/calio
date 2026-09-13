package com.calio.calendar.notification.service;

import com.calio.calendar.account.service.AccountQueryService;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
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
    private final IosPushDeviceQueryService pushDeviceQueryService;
    private final IosPushDeviceCommandService pushDeviceCommandService;

    public IosPushDeviceService(
            AccountQueryService accountQueryService,
            IosPushDeviceQueryService pushDeviceQueryService,
            IosPushDeviceCommandService pushDeviceCommandService
    ) {
        this.accountQueryService = accountQueryService;
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
                        apnsToken
                ));
        savePushDevice(pushDevice, apnsToken);
    }

    public void deactivate(Long accountId, String installationId) {
        pushDeviceQueryService.getPushDeviceIfExists(accountId, installationId)
                .ifPresent(pushDevice -> pushDeviceCommandService.deactivate(pushDevice, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<IosPushDevice> listEligiblePushDevices(Long accountId) {
        return pushDeviceQueryService.listEligiblePushDevices(accountId);
    }

    public void deactivateInvalidPushDevice(IosPushDevice pushDevice) {
        pushDeviceCommandService.deactivate(pushDevice, Instant.now());
    }

    private void deactivatePushDeviceOwnedByAnotherInstallation(
            Long accountId,
            String installationId,
            String apnsToken
    ) {
        pushDeviceQueryService.getPushDeviceWithTokenIfExists(apnsToken)
                .filter(pushDevice -> !pushDevice.belongsToInstallation(accountId, installationId))
                .ifPresent(pushDevice -> pushDeviceCommandService.deactivate(pushDevice, Instant.now()));
    }

    private void savePushDevice(
            IosPushDevice pushDevice,
            String apnsToken
    ) {
        try {
            if (pushDevice.getId() == null) {
                pushDeviceCommandService.create(pushDevice);
                return;
            }
            pushDeviceCommandService.refresh(pushDevice, apnsToken);
        } catch (DataIntegrityViolationException exception) {
            throw new CalioException(ErrorCode.NOTIFICATION_ENDPOINT_TOKEN_CONFLICT, exception);
        }
    }
}
