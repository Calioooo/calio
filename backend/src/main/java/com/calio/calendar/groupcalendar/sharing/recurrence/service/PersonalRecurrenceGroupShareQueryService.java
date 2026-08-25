package com.calio.calendar.groupcalendar.sharing.recurrence.service;

import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PersonalRecurrenceGroupShareQueryService {

    private final PersonalRecurrenceGroupShareRepository shareRepository;
    public PersonalRecurrenceGroupShareQueryService(PersonalRecurrenceGroupShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public Optional<PersonalRecurrenceGroupShare> getShareIfExists(Long recurrenceEventId, Long groupSpaceId) {
        return shareRepository.findByRecurrenceEventIdAndGroupSpaceId(recurrenceEventId, groupSpaceId);
    }

    public List<PersonalRecurrenceGroupShare> listSharesForRecurrenceEvent(Long recurrenceEventId) {
        return shareRepository.findAllByRecurrenceEventId(recurrenceEventId);
    }
}
