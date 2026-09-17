package com.calio.calendar.notification.usecase;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.repository.IosPushDeviceRepository;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RegisterIosPushDeviceUseCase {

  private final IosPushDeviceRepository pushDeviceRepository;
  private final Clock clock;

  public RegisterIosPushDeviceUseCase(IosPushDeviceRepository pushDeviceRepository, Clock clock) {
    this.pushDeviceRepository = pushDeviceRepository;
    this.clock = clock;
  }

  public void execute(Long accountId, String installationId, String apnsToken) {
    deactivatePushDeviceOwnedByAnotherInstallation(accountId, installationId, apnsToken);

    IosPushDevice pushDevice =
        pushDeviceRepository
            .findByAccountIdAndInstallationId(accountId, installationId)
            .orElseGet(() -> new IosPushDevice(accountId, installationId, apnsToken));
    savePushDevice(pushDevice, apnsToken);
  }

  private void deactivatePushDeviceOwnedByAnotherInstallation(
      Long accountId, String installationId, String apnsToken) {
    pushDeviceRepository
        .lockDeviceWithToken(apnsToken)
        .filter(pushDevice -> !pushDevice.belongsToInstallation(accountId, installationId))
        .ifPresent(this::deactivate);
  }

  private void savePushDevice(IosPushDevice pushDevice, String apnsToken) {
    pushDevice.refresh(apnsToken);
    try {
      pushDeviceRepository.saveAndFlush(pushDevice);
    } catch (DataIntegrityViolationException exception) {
      throw new CalioException(ErrorCode.NOTIFICATION_ENDPOINT_TOKEN_CONFLICT, exception);
    }
  }

  private void deactivate(IosPushDevice pushDevice) {
    pushDevice.deactivate(clock.instant());
    pushDeviceRepository.saveAndFlush(pushDevice);
  }
}
