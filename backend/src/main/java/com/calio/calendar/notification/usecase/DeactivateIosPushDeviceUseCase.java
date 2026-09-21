package com.calio.calendar.notification.usecase;

import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.repository.IosPushDeviceRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeactivateIosPushDeviceUseCase {

  private final IosPushDeviceRepository pushDeviceRepository;
  private final Clock clock;

  public DeactivateIosPushDeviceUseCase(IosPushDeviceRepository pushDeviceRepository, Clock clock) {
    this.pushDeviceRepository = pushDeviceRepository;
    this.clock = clock;
  }

  public void execute(Long accountId, String installationId) {
    pushDeviceRepository
        .findByAccountIdAndInstallationId(accountId, installationId)
        .ifPresent(this::deactivate);
  }

  private void deactivate(IosPushDevice pushDevice) {
    pushDevice.deactivate(clock.instant());
    pushDeviceRepository.saveAndFlush(pushDevice);
  }
}
