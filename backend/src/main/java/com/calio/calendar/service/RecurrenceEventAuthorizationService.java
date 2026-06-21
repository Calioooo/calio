package com.calio.calendar.service;

import com.calio.calendar.repository.entity.RecurrenceEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RecurrenceEventAuthorizationService {

    private final boolean updateEnabled;

    public RecurrenceEventAuthorizationService(
            @Value("${calio.recurrence-events.update-enabled:true}") boolean updateEnabled
    ) {
        this.updateEnabled = updateEnabled;
    }

    public boolean canUpdate(RecurrenceEvent recurrenceEvent) {
        return updateEnabled;
    }
}
