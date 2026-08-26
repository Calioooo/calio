package com.calio.calendar.groupcalendar.sharing.event.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.groupcalendar.sharing.event.repository.PersonalEventGroupShareRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonalEventGroupShareCommandService {

    private final PersonalEventGroupShareRepository shareRepository;

    public PersonalEventGroupShareCommandService(PersonalEventGroupShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public PersonalEventGroupShare createShare(PersonalEventGroupShare share) {
        try {
            return shareRepository.saveAndFlush(share);
        } catch (DataIntegrityViolationException exception) {
            throw new CalioException(ErrorCode.PERSONAL_EVENT_GROUP_SHARE_CONFLICT, exception);
        }
    }

    public void deleteShare(PersonalEventGroupShare share) {
        shareRepository.delete(share);
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
