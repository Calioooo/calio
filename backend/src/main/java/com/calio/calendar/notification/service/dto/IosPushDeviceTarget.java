package com.calio.calendar.notification.service.dto;

import com.calio.calendar.notification.domain.IosPushDevice;

public record IosPushDeviceTarget(Long pushDeviceId, String apnsToken) {

  public static IosPushDeviceTarget from(IosPushDevice pushDevice) {
    return new IosPushDeviceTarget(pushDevice.getId(), pushDevice.getApnsToken());
  }
}
