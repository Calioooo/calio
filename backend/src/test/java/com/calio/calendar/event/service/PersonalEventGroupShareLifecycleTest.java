package com.calio.calendar.event.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.groupcalendar.sharing.event.repository.PersonalEventGroupShareRepository;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:personal-event-group-share-lifecycle-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PersonalEventGroupShareLifecycleTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private PersonalEventGroupShareRepository shareRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @DisplayName("개인 원본 일정 삭제는 해당 share mapping만 hard-delete한다")
    void givenSharedEvents_whenDeleteSourceEvent_thenDeletesOnlySourceShares() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B"));
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(account.getId(), "그룹", null)
        );
        Event deletedSource = eventRepository.saveAndFlush(event(account, tag, "삭제할 일정"));
        Event remainingSource = eventRepository.saveAndFlush(event(account, tag, "남길 일정"));
        shareRepository.saveAndFlush(new PersonalEventGroupShare(deletedSource, groupSpace));
        PersonalEventGroupShare remainingShare = shareRepository.saveAndFlush(
                new PersonalEventGroupShare(remainingSource, groupSpace)
        );

        // when
        eventService.deleteEvent(account.getId(), deletedSource.getId());

        // then
        assertThat(shareRepository.findAllByEventId(deletedSource.getId())).isEmpty();
        assertThat(shareRepository.findAllByEventId(remainingSource.getId()))
                .extracting(PersonalEventGroupShare::getId)
                .containsExactly(remainingShare.getId());
        assertThat(eventRepository.existsById(deletedSource.getId())).isFalse();
        assertThat(eventRepository.existsById(remainingSource.getId())).isTrue();
    }

    private Event event(Account account, Tag tag, String title) {
        return new Event(
                title,
                null,
                Instant.parse("2028-01-01T09:00:00Z"),
                Instant.parse("2028-01-01T10:00:00Z"),
                false,
                "UTC",
                null,
                tag,
                account
        );
    }
}
