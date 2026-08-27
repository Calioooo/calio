package com.calio.calendar.sharing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.sharing.event.repository.PersonalEventGroupShareRepository;
import com.calio.calendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:personal-schedule-group-share-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PersonalScheduleGroupShareRepositoryTest {

    private static final Instant START_AT = Instant.parse("2026-08-01T09:00:00Z");

    @Autowired private PersonalEventGroupShareRepository eventShareRepository;
    @Autowired private PersonalRecurrenceGroupShareRepository recurrenceShareRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private RecurrenceEventRepository recurrenceEventRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private GroupSpaceRepository groupSpaceRepository;
    @Autowired private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        eventShareRepository.deleteAll();
        recurrenceShareRepository.deleteAll();
        eventRepository.deleteAll();
        recurrenceEventRepository.deleteAll();
        tagRepository.deleteAll();
        groupSpaceRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("단건 일정 mapping은 target별 익명 상태와 변경되지 않는 공개 UUID를 가진다")
    void eventShareKeepsTargetSpecificAnonymousStateAndPublicUuid() {
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(Tag.personalDefault("기타", "#64748B"));
        Event event = eventRepository.saveAndFlush(new Event(
                "일정", null, START_AT, START_AT.plusSeconds(3600), false, "UTC", null, tag, account
        ));
        GroupSpace firstGroup = groupSpaceRepository.saveAndFlush(new GroupSpace(account.getId(), "first", null));
        GroupSpace secondGroup = groupSpaceRepository.saveAndFlush(new GroupSpace(account.getId(), "second", null));

        PersonalEventGroupShare first = eventShareRepository.saveAndFlush(
                PersonalEventGroupShare.create(event, firstGroup, true)
        );
        PersonalEventGroupShare second = eventShareRepository.saveAndFlush(
                PersonalEventGroupShare.create(event, secondGroup, false)
        );
        var firstPublicShareId = first.getPublicShareId();

        first.changeAnonymous(false);
        eventShareRepository.flush();

        assertThat(first.getPublicShareId()).isEqualTo(firstPublicShareId);
        assertThat(first.isAnonymous()).isFalse();
        assertThat(second.isAnonymous()).isFalse();
        assertThat(second.getPublicShareId()).isNotEqualTo(firstPublicShareId);
    }

    @Test
    @DisplayName("같은 단건 일정과 Group Space 조합은 하나의 mapping만 저장한다")
    void eventSharePersistsOnlyOneMappingPerSourceAndGroupSpace() {
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(Tag.personalDefault("기타", "#64748B"));
        Event event = eventRepository.saveAndFlush(new Event(
                "일정", null, START_AT, START_AT.plusSeconds(3600), false, "UTC", null, tag, account
        ));
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(new GroupSpace(account.getId(), "group", null));
        eventShareRepository.saveAndFlush(PersonalEventGroupShare.create(event, groupSpace, false));

        assertThatThrownBy(() -> eventShareRepository.saveAndFlush(
                PersonalEventGroupShare.create(event, groupSpace, true)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("반복 mapping은 master 전체와 Group Space만 연결하고 공개 UUID를 가진다")
    void recurrenceSharePersistsOnlyMasterLevelState() {
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(Tag.personalDefault("기타", "#64748B"));
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.saveAndFlush(new RecurrenceEvent(
                "반복", null,
                new RecurrenceSchedule(START_AT, START_AT.plusSeconds(3600), false, "UTC"),
                List.of("RRULE:FREQ=DAILY"), tag, account
        ));
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(new GroupSpace(account.getId(), "group", null));

        PersonalRecurrenceGroupShare share = recurrenceShareRepository.saveAndFlush(
                PersonalRecurrenceGroupShare.create(recurrenceEvent, groupSpace, true)
        );

        assertThat(share.getRecurrenceEvent()).isSameAs(recurrenceEvent);
        assertThat(share.getGroupSpace()).isSameAs(groupSpace);
        assertThat(share.isAnonymous()).isTrue();
        assertThat(share.getPublicShareId()).isNotNull();
    }
}
