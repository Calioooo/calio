package com.calio.calendar.groupcalendar.sharing.event.service;

import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.groupcalendar.sharing.event.repository.PersonalEventGroupShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonalEventGroupShareCommandService {

    private final PersonalEventGroupShareRepository shareRepository;

    public PersonalEventGroupShareCommandService(PersonalEventGroupShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public boolean createShare(PersonalEventGroupShare share) {
        return shareRepository.insertIgnore(
                share.getEvent().getId(), share.getGroupSpace().getId(), share.getPublicShareId().toString()
        ) == 1;
    }

    public void deleteShare(PersonalEventGroupShare share) {
        shareRepository.delete(share);
    }

    public void changeAnonymous(PersonalEventGroupShare share, boolean anonymous) {
        share.changeAnonymous(anonymous);
        shareRepository.flush();
    }

    public void deleteAllForEvent(Long eventId) {
        shareRepository.deleteAllByEventId(eventId);
    }

    public void deleteAllForMemberInGroupSpace(Long groupSpaceId, Long accountId) {
        shareRepository.deleteAllByGroupSpaceIdAndAccountId(groupSpaceId, accountId);
    }

    public void deleteAllInGroupSpace(Long groupSpaceId) {
        shareRepository.deleteAllByGroupSpaceId(groupSpaceId);
    }
}
