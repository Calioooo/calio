package com.calio.calendar.sharing.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
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

    public List<PersonalRecurrenceGroupShare> listByRecurrenceEventId(Long recurrenceEventId) {
        return shareRepository.findAllByRecurrenceEvent_IdOrderByGroupSpace_IdAsc(recurrenceEventId);
    }

    public PersonalRecurrenceGroupShare getByRecurrenceEventIdAndGroupSpaceId(
            Long recurrenceEventId,
            Long groupSpaceId
    ) {
        return shareRepository.findByRecurrenceEvent_IdAndGroupSpace_Id(recurrenceEventId, groupSpaceId)
                .orElseThrow(() -> new CalioException(ErrorCode.PERSONAL_SCHEDULE_SHARE_NOT_FOUND));
    }

    public List<PersonalRecurrenceGroupShare> listByGroupSpaceId(Long groupSpaceId) {
        return shareRepository.findAllByGroupSpaceId(groupSpaceId);
    }
}
