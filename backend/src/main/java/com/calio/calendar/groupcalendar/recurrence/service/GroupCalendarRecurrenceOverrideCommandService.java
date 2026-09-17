package com.calio.calendar.groupcalendar.recurrence.service;

import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceOverrideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupCalendarRecurrenceOverrideCommandService {

    private final GroupCalendarRecurrenceOverrideRepository overrideRepository;

    public GroupCalendarRecurrenceOverrideCommandService(
            GroupCalendarRecurrenceOverrideRepository overrideRepository
    ) {
        this.overrideRepository = overrideRepository;
    }

    public GroupCalendarRecurrenceOverride createOrUpdateOverride(
            GroupCalendarRecurrenceOverride override
    ) {
        return overrideRepository.saveAndFlush(override);
    }
}
