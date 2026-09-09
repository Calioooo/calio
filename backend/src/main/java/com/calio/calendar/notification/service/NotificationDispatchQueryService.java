package com.calio.calendar.notification.service;

import com.calio.calendar.notification.repository.NotificationDispatchRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationDispatchQueryService {

    private final NotificationDispatchRepository dispatchRepository;

    public NotificationDispatchQueryService(NotificationDispatchRepository dispatchRepository) {
        this.dispatchRepository = dispatchRepository;
    }

    public boolean hasDispatchClaim(Long accountId, String type, String key, Instant scheduledAt) {
        return dispatchRepository.findByAccount_IdAndNotificationTypeAndScheduleKeyAndScheduledAt(
                accountId,
                type,
                key,
                scheduledAt
        ).isPresent();
    }
}
