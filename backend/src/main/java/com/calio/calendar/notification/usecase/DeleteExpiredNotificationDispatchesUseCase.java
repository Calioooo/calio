package com.calio.calendar.notification.usecase;

import com.calio.calendar.notification.repository.NotificationDispatchRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteExpiredNotificationDispatchesUseCase {

  private final NotificationDispatchRepository dispatchRepository;

  public DeleteExpiredNotificationDispatchesUseCase(
      NotificationDispatchRepository dispatchRepository) {
    this.dispatchRepository = dispatchRepository;
  }

  @Transactional
  public int execute(Instant cutoff) {
    return dispatchRepository.deleteScheduledBefore(cutoff);
  }
}
