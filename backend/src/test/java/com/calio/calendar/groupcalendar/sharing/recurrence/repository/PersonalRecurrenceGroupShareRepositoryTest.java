package com.calio.calendar.groupcalendar.sharing.recurrence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:personal-recurrence-group-share-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PersonalRecurrenceGroupShareRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private PersonalRecurrenceGroupShareRepository shareRepository;

    @Autowired
    private RecurrenceEventRepository recurrenceEventRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @DisplayName("반복 일정 공유는 반복 마스터와 Group Space의 매핑만 저장한다")
    void givenRecurrenceEventAndGroupSpace_whenSave_thenPersistsMasterMapping() {
        // given
        Fixture fixture = fixture();
        PersonalRecurrenceGroupShare share = new PersonalRecurrenceGroupShare(
                fixture.recurrenceEvent(),
                fixture.groupSpace()
        );

        // when
        PersonalRecurrenceGroupShare savedShare = shareRepository.saveAndFlush(share);

        // then
        assertThat(savedShare.getRecurrenceEvent().getId())
                .isEqualTo(fixture.recurrenceEvent().getId());
        assertThat(savedShare.getGroupSpace().getId()).isEqualTo(fixture.groupSpace().getId());
    }

    @Test
    @DisplayName("같은 반복 원본은 같은 Group Space에 두 번 공유할 수 없다")
    void givenDuplicateRecurrenceAndGroupSpace_whenSave_thenRejectsDuplicateShare() {
        // given
        Fixture fixture = fixture();
        shareRepository.saveAndFlush(new PersonalRecurrenceGroupShare(
                fixture.recurrenceEvent(),
                fixture.groupSpace()
        ));

        // when, then
        assertThatThrownBy(() -> shareRepository.saveAndFlush(new PersonalRecurrenceGroupShare(
                fixture.recurrenceEvent(),
                fixture.groupSpace()
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture fixture() {
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B"));
        RecurrenceEvent recurrenceEvent = recurrenceEventRepository.saveAndFlush(new RecurrenceEvent(
                "반복 일정",
                null,
                new RecurrenceSchedule(
                        Instant.parse("2028-01-01T09:00:00Z"),
                        Instant.parse("2028-01-01T10:00:00Z"),
                        false,
                        "UTC"
                ),
                List.of("RRULE:FREQ=WEEKLY"),
                tag,
                account
        ));
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(account.getId(), "그룹", null)
        );
        return new Fixture(recurrenceEvent, groupSpace);
    }

    private record Fixture(RecurrenceEvent recurrenceEvent, GroupSpace groupSpace) {
    }
}
