package com.calio.calendar.groupcalendar.sharing.event.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.groupcalendar.sharing.event.repository.PersonalEventGroupShareRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PersonalEventGroupShareCommandService {

    private static final Set<String> SHARE_UNIQUE_CONSTRAINTS = Set.of(
            "uk_personal_event_group_share_event_group"
    );

    private final PersonalEventGroupShareRepository shareRepository;

    public PersonalEventGroupShareCommandService(PersonalEventGroupShareRepository shareRepository) {
        this.shareRepository = shareRepository;
    }

    public PersonalEventGroupShare createShare(PersonalEventGroupShare share) {
        try {
            return shareRepository.saveAndFlush(share);
        } catch (DataIntegrityViolationException exception) {
            if (isShareConflict(exception)) {
                throw new CalioException(ErrorCode.PERSONAL_EVENT_GROUP_SHARE_CONFLICT, exception);
            }
            throw exception;
        }
    }

    public void updateRepresentation(
            PersonalEventGroupShare share,
            boolean showOriginalDetails,
            String overrideTitle,
            Instant overrideStartAt,
            Instant overrideEndAt,
            Boolean overrideAllDay
    ) {
        share.updateRepresentation(
                showOriginalDetails,
                overrideTitle,
                overrideStartAt,
                overrideEndAt,
                overrideAllDay
        );
        shareRepository.flush();
    }

    private boolean isShareConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && containsShareConstraint(message)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean containsShareConstraint(String message) {
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        return SHARE_UNIQUE_CONSTRAINTS.stream().anyMatch(normalizedMessage::contains);
    }
}
