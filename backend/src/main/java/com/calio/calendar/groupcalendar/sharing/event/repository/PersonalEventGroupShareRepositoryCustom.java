package com.calio.calendar.groupcalendar.sharing.event.repository;

import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;

public interface PersonalEventGroupShareRepositoryCustom {

    PersonalEventGroupShare createShare(PersonalEventGroupShare share);
}
