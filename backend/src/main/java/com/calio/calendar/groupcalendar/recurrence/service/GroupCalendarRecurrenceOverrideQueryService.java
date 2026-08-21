package com.calio.calendar.groupcalendar.recurrence.service;

import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceOverride;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceOverrideRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupCalendarRecurrenceOverrideQueryService {

    private final GroupCalendarRecurrenceOverrideRepository overrideRepository;

    public GroupCalendarRecurrenceOverrideQueryService(
            GroupCalendarRecurrenceOverrideRepository overrideRepository
    ) {
        this.overrideRepository = overrideRepository;
    }

    public Optional<GroupCalendarRecurrenceOverride> getOverrideIfExists(
            Long recurrenceId,
            Instant originStartAt
    ) {
        return overrideRepository.findByRecurrenceEvent_IdAndOriginStartAt(recurrenceId, originStartAt);
    }

    public List<GroupCalendarRecurrenceOverride> listOverrides(
            Long recurrenceId,
            Collection<Instant> originStartAts
    ) {
        return overrideRepository.findByRecurrenceEvent_IdAndOriginStartAtIn(recurrenceId, originStartAts);
    }

    public List<GroupCalendarRecurrenceOverride> listMovedInOverrides(
            Long groupSpaceId,
            Instant from,
            Instant to
    ) {
        return overrideRepository
                .findByRecurrenceEvent_GroupSpace_IdAndDeletedAtIsNullAndStartAtLessThanAndEndAtGreaterThan(
                        groupSpaceId,
                        to,
                        from
                );
    }
}
