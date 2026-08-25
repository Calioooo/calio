package com.calio.calendar.groupcalendar.sharing.recurrence.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonalRecurrenceGroupShareCommandService {

    private final PersonalRecurrenceGroupShareRepository shareRepository;
    public PersonalRecurrenceGroupShareCommandService(PersonalRecurrenceGroupShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public PersonalRecurrenceGroupShare createShare(PersonalRecurrenceGroupShare share) {
        try {
            return shareRepository.saveAndFlush(share);
        } catch (DataIntegrityViolationException exception) {
            throw new CalioException(ErrorCode.PERSONAL_RECURRENCE_GROUP_SHARE_CONFLICT, exception);
        }
    }
}
