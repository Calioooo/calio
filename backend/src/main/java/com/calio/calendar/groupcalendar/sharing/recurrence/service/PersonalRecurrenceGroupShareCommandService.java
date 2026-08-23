package com.calio.calendar.groupcalendar.sharing.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareOccurrenceOverride;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareSelectedOrigin;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareOccurrenceOverrideRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareSelectedOriginRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonalRecurrenceGroupShareCommandService {

    private final PersonalRecurrenceGroupShareRepository shareRepository;
    private final PersonalRecurrenceGroupShareSelectedOriginRepository selectedOriginRepository;
    private final PersonalRecurrenceGroupShareOccurrenceOverrideRepository occurrenceOverrideRepository;

    public PersonalRecurrenceGroupShareCommandService(
            PersonalRecurrenceGroupShareRepository shareRepository,
            PersonalRecurrenceGroupShareSelectedOriginRepository selectedOriginRepository,
            PersonalRecurrenceGroupShareOccurrenceOverrideRepository occurrenceOverrideRepository
    ) {
        this.shareRepository = shareRepository;
        this.selectedOriginRepository = selectedOriginRepository;
        this.occurrenceOverrideRepository = occurrenceOverrideRepository;
    }

    public PersonalRecurrenceGroupShare createShare(PersonalRecurrenceGroupShare share) {
        try {
            return shareRepository.saveAndFlush(share);
        } catch (DataIntegrityViolationException exception) {
            throw new CalioException(ErrorCode.PERSONAL_RECURRENCE_GROUP_SHARE_CONFLICT, exception);
        }
    }

    public PersonalRecurrenceGroupShareSelectedOrigin selectOrigin(
            PersonalRecurrenceGroupShareSelectedOrigin selectedOrigin
    ) {
        return selectedOriginRepository.saveAndFlush(selectedOrigin);
    }

    public PersonalRecurrenceGroupShareOccurrenceOverride createOccurrenceOverride(
            PersonalRecurrenceGroupShareOccurrenceOverride occurrenceOverride
    ) {
        return occurrenceOverrideRepository.saveAndFlush(occurrenceOverride);
    }

    public void deleteAllForRecurrenceEvent(Long recurrenceEventId) {
        occurrenceOverrideRepository.deleteAllByRecurrenceEventId(recurrenceEventId);
        selectedOriginRepository.deleteAllByRecurrenceEventId(recurrenceEventId);
        shareRepository.deleteAllByRecurrenceEventId(recurrenceEventId);
    }

    public void deleteAllForMemberInGroupSpace(Long groupSpaceId, Long accountId) {
        occurrenceOverrideRepository.deleteAllByGroupSpaceIdAndAccountId(groupSpaceId, accountId);
        selectedOriginRepository.deleteAllByGroupSpaceIdAndAccountId(groupSpaceId, accountId);
        shareRepository.deleteAllByGroupSpaceIdAndAccountId(groupSpaceId, accountId);
    }

    public void deleteAllInGroupSpace(Long groupSpaceId) {
        occurrenceOverrideRepository.deleteAllByGroupSpaceId(groupSpaceId);
        selectedOriginRepository.deleteAllByGroupSpaceId(groupSpaceId);
        shareRepository.deleteAllByGroupSpaceId(groupSpaceId);
    }
}
