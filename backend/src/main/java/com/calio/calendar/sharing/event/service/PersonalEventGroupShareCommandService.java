package com.calio.calendar.sharing.event.service;

import com.calio.calendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.sharing.event.repository.PersonalEventGroupShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonalEventGroupShareCommandService {

    private final PersonalEventGroupShareRepository shareRepository;

    public PersonalEventGroupShareCommandService(PersonalEventGroupShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public void deleteAllForSourceEvent(Long eventId) {
        shareRepository.deleteAllByEventId(eventId);
    }

    public void deleteAllForGroupSpace(Long groupSpaceId) {
        shareRepository.deleteAllByGroupSpaceId(groupSpaceId);
    }

    public void deleteAllForMemberInGroupSpace(Long groupSpaceId, Long memberId) {
        shareRepository.deleteAllByGroupSpaceIdAndMemberId(groupSpaceId, memberId);
    }

    public boolean createIfAbsent(PersonalEventGroupShare share) {
        return shareRepository.insertIgnore(
                share.getEvent().getId(),
                share.getGroupSpace().getId(),
                share.isAnonymous(),
                share.getPublicShareId().toString()
        ) == 1;
    }
}
