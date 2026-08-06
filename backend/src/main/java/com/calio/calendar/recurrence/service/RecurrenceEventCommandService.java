package com.calio.calendar.recurrence.service;

import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import org.springframework.stereotype.Service;

@Service
public class RecurrenceEventCommandService {

    private final RecurrenceEventRepository recurrenceEventRepository;

    public RecurrenceEventCommandService(RecurrenceEventRepository recurrenceEventRepository) {
        this.recurrenceEventRepository = recurrenceEventRepository;
    }

    public void changeTagForRecurrenceEvents(Long accountId, Tag sourceTag, Tag targetTag) {
        recurrenceEventRepository.reassignAllByTagAndAccountId(sourceTag, targetTag, accountId);
    }
}
