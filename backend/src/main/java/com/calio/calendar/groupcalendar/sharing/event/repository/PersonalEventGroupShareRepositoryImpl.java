package com.calio.calendar.groupcalendar.sharing.event.repository;

import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import jakarta.persistence.EntityManager;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;

@Repository
class PersonalEventGroupShareRepositoryImpl implements PersonalEventGroupShareRepositoryCustom {

    private static final String SHARE_UNIQUE_CONSTRAINT = "uk_personal_event_group_share_event_group";

    private final EntityManager entityManager;

    PersonalEventGroupShareRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public PersonalEventGroupShare createShare(PersonalEventGroupShare share) {
        try {
            entityManager.persist(share);
            entityManager.flush();
            return share;
        } catch (RuntimeException exception) {
            if (isDuplicateShare(exception)) {
                throw new PersonalEventGroupShareDuplicateException(exception);
            }
            throw exception;
        }
    }

    private boolean isDuplicateShare(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (isShareConstraintViolation(cause)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isShareConstraintViolation(Throwable throwable) {
        if (!(throwable instanceof ConstraintViolationException exception)) {
            return false;
        }
        String constraintName = exception.getConstraintName();
        return constraintName != null && constraintName
                .toLowerCase(Locale.ROOT)
                .contains(SHARE_UNIQUE_CONSTRAINT);
    }
}
