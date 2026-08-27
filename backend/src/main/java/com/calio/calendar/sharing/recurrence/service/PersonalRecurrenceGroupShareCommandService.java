package com.calio.calendar.sharing.recurrence.service;

import com.calio.calendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonalRecurrenceGroupShareCommandService {

    private final PersonalRecurrenceGroupShareRepository shareRepository;

    public PersonalRecurrenceGroupShareCommandService(PersonalRecurrenceGroupShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public void deleteAllForSourceRecurrence(Long recurrenceEventId) {
        shareRepository.deleteAllByRecurrenceEventId(recurrenceEventId);
    }

    public void deleteAllForGroupSpace(Long groupSpaceId) {
        shareRepository.deleteAllByGroupSpaceId(groupSpaceId);
    }

    public void deleteAllForMemberInGroupSpace(Long groupSpaceId, Long memberId) {
        shareRepository.deleteAllByGroupSpaceIdAndMemberId(groupSpaceId, memberId);
    }
}
