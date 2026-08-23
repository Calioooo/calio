package com.calio.calendar.groupcalendar.sharing.event.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
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
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:personal-event-group-share-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PersonalEventGroupShareRepositoryTest {

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
    @DisplayName("개인 단건 일정 공유는 원본 일정과 Group Space 조합으로 조회한다")
    void givenShare_whenFindByEventAndGroupSpace_thenReturnsShareWithAssociations() {
        // given
        Fixture fixture = fixture();
        PersonalEventGroupShare share = shareRepository.saveAndFlush(
                new PersonalEventGroupShare(fixture.event(), fixture.groupSpace())
        );

        // when
        PersonalEventGroupShare foundShare = shareRepository.findByEventIdAndGroupSpaceId(
                fixture.event().getId(),
                fixture.groupSpace().getId()
        ).orElseThrow();

        // then
        assertThat(foundShare.getId()).isEqualTo(share.getId());
        assertThat(foundShare.getEvent().getId()).isEqualTo(fixture.event().getId());
        assertThat(foundShare.getGroupSpace().getId()).isEqualTo(fixture.groupSpace().getId());
    }

    @Test
    @DisplayName("같은 개인 단건 일정은 같은 Group Space에 두 번 공유할 수 없다")
    void givenDuplicateEventAndGroupSpace_whenSave_thenRejectsDuplicateShare() {
        // given
        Fixture fixture = fixture();
        shareRepository.saveAndFlush(new PersonalEventGroupShare(fixture.event(), fixture.groupSpace()));

        // when, then
        assertThatThrownBy(() -> shareRepository.saveAndFlush(
                new PersonalEventGroupShare(fixture.event(), fixture.groupSpace())
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture() {
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B"));
        Event event = eventRepository.saveAndFlush(new Event(
                "개인 일정",
                null,
                Instant.parse("2028-01-01T09:00:00Z"),
                Instant.parse("2028-01-01T10:00:00Z"),
                false,
                "UTC",
                null,
                tag,
                account
        ));
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(account.getId(), "그룹", null)
        );
        return new Fixture(event, groupSpace);
    }

    private record Fixture(Event event, GroupSpace groupSpace) {
    }
}
