package com.calio.calendar.groupcalendar.sharing.event.service;

import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.groupcalendar.sharing.event.repository.PersonalEventGroupShareRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PersonalEventGroupShareQueryService {

    private final PersonalEventGroupShareRepository shareRepository;

    public PersonalEventGroupShareQueryService(PersonalEventGroupShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public Optional<PersonalEventGroupShare> getShareIfExists(Long eventId, Long groupSpaceId) {
        return shareRepository.findByEventIdAndGroupSpaceId(eventId, groupSpaceId);
    }

    public List<PersonalEventGroupShare> listSharesForEvent(Long eventId) {
        return shareRepository.findAllByEventId(eventId);
    }

    public List<PersonalEventGroupShare> listSharesInGroupSpace(Long groupSpaceId) {
        return shareRepository.findAllByGroupSpaceId(groupSpaceId);
    }

    public List<PersonalEventGroupShare> listSharesForEventsAndGroupSpaces(
            List<Long> eventIds, List<Long> groupSpaceIds
    ) {
        return shareRepository.findAllByEventIdInAndGroupSpaceIdIn(eventIds, groupSpaceIds);
    }
}
