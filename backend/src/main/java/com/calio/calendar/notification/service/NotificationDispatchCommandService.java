package com.calio.calendar.notification.service;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.notification.domain.NotificationDispatch;
import com.calio.calendar.notification.repository.NotificationDispatchRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDispatchCommandService {

    private final NotificationDispatchRepository dispatchRepository;

    public NotificationDispatchCommandService(NotificationDispatchRepository dispatchRepository) {
        this.dispatchRepository = dispatchRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationDispatch create(
            Account account,
            String type,
            String key,
            Instant scheduledAt,
            LocalDate targetDate,
            String title,
            String groupName
    ) {
        return dispatchRepository.saveAndFlush(new NotificationDispatch(
                account,
                type,
                key,
                scheduledAt,
                targetDate,
                title,
                groupName
        ));
    }
}
