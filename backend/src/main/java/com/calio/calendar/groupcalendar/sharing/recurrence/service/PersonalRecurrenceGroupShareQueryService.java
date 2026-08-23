package com.calio.calendar.groupcalendar.sharing.recurrence.service;

import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareOccurrenceOverride;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareSelectedOrigin;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareOccurrenceOverrideRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareSelectedOriginRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PersonalRecurrenceGroupShareQueryService {

    private final PersonalRecurrenceGroupShareRepository shareRepository;
    private final PersonalRecurrenceGroupShareSelectedOriginRepository selectedOriginRepository;
    private final PersonalRecurrenceGroupShareOccurrenceOverrideRepository occurrenceOverrideRepository;

    public PersonalRecurrenceGroupShareQueryService(
            PersonalRecurrenceGroupShareRepository shareRepository,
            PersonalRecurrenceGroupShareSelectedOriginRepository selectedOriginRepository,
            PersonalRecurrenceGroupShareOccurrenceOverrideRepository occurrenceOverrideRepository
    ) {
        this.shareRepository = shareRepository;
        this.selectedOriginRepository = selectedOriginRepository;
        this.occurrenceOverrideRepository = occurrenceOverrideRepository;
    }

    public Optional<PersonalRecurrenceGroupShare> getShareIfExists(Long recurrenceEventId, Long groupSpaceId) {
        return shareRepository.findByRecurrenceEventIdAndGroupSpaceId(recurrenceEventId, groupSpaceId);
    }

    public List<PersonalRecurrenceGroupShare> listSharesForRecurrenceEvent(Long recurrenceEventId) {
        return shareRepository.findAllByRecurrenceEventId(recurrenceEventId);
    }

    public List<PersonalRecurrenceGroupShare> listSharesInGroupSpace(Long groupSpaceId) {
        return shareRepository.findAllByGroupSpaceId(groupSpaceId);
    }

    public List<PersonalRecurrenceGroupShareSelectedOrigin> listSelectedOrigins(Long shareId) {
        return selectedOriginRepository.findAllByShareId(shareId);
    }

    public Optional<PersonalRecurrenceGroupShareOccurrenceOverride> getOccurrenceOverrideIfExists(
            Long shareId,
            Instant originStartAt
    ) {
        return occurrenceOverrideRepository.findByShareIdAndOriginStartAt(shareId, originStartAt);
    }

    public List<PersonalRecurrenceGroupShareOccurrenceOverride> listOccurrenceOverrides(Long shareId) {
        return occurrenceOverrideRepository.findAllByShareId(shareId);
    }
}
