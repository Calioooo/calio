package com.calio.calendar.notification.service;

import com.calio.calendar.notification.domain.IosPushDevice;
import com.calio.calendar.notification.repository.IosPushDeviceRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IosPushDeviceQueryService {

    private final IosPushDeviceRepository pushDeviceRepository;

    public IosPushDeviceQueryService(IosPushDeviceRepository pushDeviceRepository) {
        this.pushDeviceRepository = pushDeviceRepository;
    }

    public Optional<IosPushDevice> getPushDeviceIfExists(Long accountId, String installationId) {
        return pushDeviceRepository.findByAccount_IdAndInstallationId(accountId, installationId);
    }

    public Optional<IosPushDevice> getPushDeviceWithTokenIfExists(String apnsToken) {
        return pushDeviceRepository.lockDeviceWithToken(apnsToken);
    }

    public List<IosPushDevice> listEligiblePushDevices(Long accountId) {
        return pushDeviceRepository.findByAccount_IdAndActiveTrueAndApnsTokenIsNotNull(accountId);
    }
}
