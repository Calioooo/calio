package com.calio.calendar.notification.service;

import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.repository.IosPushDeviceRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IosPushDeviceCommandService {

    private final IosPushDeviceRepository pushDeviceRepository;

    public IosPushDeviceCommandService(IosPushDeviceRepository pushDeviceRepository) {
        this.pushDeviceRepository = pushDeviceRepository;
    }

    public IosPushDevice create(IosPushDevice pushDevice) {
        return pushDeviceRepository.saveAndFlush(pushDevice);
    }

    public void refresh(IosPushDevice pushDevice, String apnsToken) {
        pushDevice.refresh(apnsToken);
        pushDeviceRepository.saveAndFlush(pushDevice);
    }

    public void deactivate(IosPushDevice pushDevice, Instant now) {
        pushDevice.deactivate(now);
        pushDeviceRepository.saveAndFlush(pushDevice);
    }
}
