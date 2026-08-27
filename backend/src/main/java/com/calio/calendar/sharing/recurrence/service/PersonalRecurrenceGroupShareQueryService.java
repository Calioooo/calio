package com.calio.calendar.sharing.recurrence.service;

import com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PersonalRecurrenceGroupShareQueryService {

    private final PersonalRecurrenceGroupShareRepository shareRepository;

    public PersonalRecurrenceGroupShareQueryService(PersonalRecurrenceGroupShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public List<PersonalRecurrenceGroupShare> listExistingShares(
            Collection<Long> recurrenceEventIds,
            Collection<Long> groupSpaceIds
    ) {
        return shareRepository.findAllByRecurrenceEventIdsAndGroupSpaceIds(
                recurrenceEventIds,
                groupSpaceIds
        );
    }
}
