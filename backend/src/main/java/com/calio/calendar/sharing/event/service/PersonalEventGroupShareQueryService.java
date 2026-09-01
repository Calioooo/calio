package com.calio.calendar.sharing.event.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.sharing.event.repository.PersonalEventGroupShareRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PersonalEventGroupShareQueryService {

    private final PersonalEventGroupShareRepository shareRepository;

    public PersonalEventGroupShareQueryService(PersonalEventGroupShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public List<PersonalEventGroupShare> listExistingShares(
            Collection<Long> eventIds,
            Collection<Long> groupSpaceIds
    ) {
        return shareRepository.findAllByEventIdsAndGroupSpaceIds(eventIds, groupSpaceIds);
    }

    public List<PersonalEventGroupShare> listByEventId(Long eventId) {
        return shareRepository.findAllByEvent_IdOrderByGroupSpace_IdAsc(eventId);
    }

    public PersonalEventGroupShare getByEventIdAndGroupSpaceId(Long eventId, Long groupSpaceId) {
        return shareRepository.findByEvent_IdAndGroupSpace_Id(eventId, groupSpaceId)
                .orElseThrow(() -> new CalioException(ErrorCode.PERSONAL_SCHEDULE_SHARE_NOT_FOUND));
    }

    public List<PersonalEventGroupShare> listByGroupSpaceId(Long groupSpaceId) {
        return shareRepository.findAllByGroupSpaceId(groupSpaceId);
    }
}
