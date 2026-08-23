package com.calio.calendar.recurrence.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareOccurrenceOverride;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareScope;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareSelectedOrigin;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareOccurrenceOverrideRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareSelectedOriginRepository;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:personal-recurrence-group-share-lifecycle-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PersonalRecurrenceGroupShareLifecycleTest {

    private static final Instant ORIGIN_START_AT = Instant.parse("2028-01-08T09:00:00Z");

    @Autowired
    private RecurrenceEventService recurrenceEventService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private PersonalRecurrenceGroupShareOccurrenceOverrideRepository occurrenceOverrideRepository;

    @Autowired
    private PersonalRecurrenceGroupShareRepository shareRepository;

    @Autowired
    private PersonalRecurrenceGroupShareSelectedOriginRepository selectedOriginRepository;

    @Autowired
    private RecurrenceEventRepository recurrenceEventRepository;

    @Autowired
    private TagRepository tagRepository;

    @Test
    @DisplayName("개인 반복 원본 삭제는 share와 선택 origin 및 occurrence override만 hard-delete한다")
    void givenSharedRecurrenceEvents_whenDeleteSource_thenDeletesOnlySourceShareState() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        Tag tag = tagRepository.saveAndFlush(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B"));
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(account.getId(), "그룹", null)
        );
        RecurrenceEvent deletedSource = recurrenceEventRepository.saveAndFlush(
                recurrenceEvent(account, tag, "삭제할 반복 일정")
        );
        RecurrenceEvent remainingSource = recurrenceEventRepository.saveAndFlush(
                recurrenceEvent(account, tag, "남길 반복 일정")
        );
        PersonalRecurrenceGroupShare deletedShare = shareRepository.saveAndFlush(
                new PersonalRecurrenceGroupShare(
                        deletedSource,
                        groupSpace,
                        PersonalRecurrenceGroupShareScope.SELECTED_OCCURRENCES
                )
        );
        PersonalRecurrenceGroupShare remainingShare = shareRepository.saveAndFlush(
                new PersonalRecurrenceGroupShare(
                        remainingSource,
                        groupSpace,
                        PersonalRecurrenceGroupShareScope.SELECTED_OCCURRENCES
                )
        );
        selectedOriginRepository.saveAndFlush(
                new PersonalRecurrenceGroupShareSelectedOrigin(deletedShare, ORIGIN_START_AT)
        );
        selectedOriginRepository.saveAndFlush(
                new PersonalRecurrenceGroupShareSelectedOrigin(remainingShare, ORIGIN_START_AT)
        );
        occurrenceOverrideRepository.saveAndFlush(
                new PersonalRecurrenceGroupShareOccurrenceOverride(deletedShare, ORIGIN_START_AT)
        );
        occurrenceOverrideRepository.saveAndFlush(
                new PersonalRecurrenceGroupShareOccurrenceOverride(remainingShare, ORIGIN_START_AT)
        );

        // when
        recurrenceEventService.deleteRecurrenceEvent(account.getId(), deletedSource.getId());

        // then
        assertThat(shareRepository.findAllByRecurrenceEventId(deletedSource.getId())).isEmpty();
        assertThat(selectedOriginRepository.findByShareIdAndOriginStartAt(deletedShare.getId(), ORIGIN_START_AT))
                .isEmpty();
        assertThat(occurrenceOverrideRepository.findByShareIdAndOriginStartAt(
                deletedShare.getId(),
                ORIGIN_START_AT
        )).isEmpty();
        assertThat(shareRepository.findAllByRecurrenceEventId(remainingSource.getId()))
                .extracting(PersonalRecurrenceGroupShare::getId)
                .containsExactly(remainingShare.getId());
        assertThat(selectedOriginRepository.findByShareIdAndOriginStartAt(remainingShare.getId(), ORIGIN_START_AT))
                .isPresent();
        assertThat(occurrenceOverrideRepository.findByShareIdAndOriginStartAt(
                remainingShare.getId(),
                ORIGIN_START_AT
        )).isPresent();
        assertThat(recurrenceEventRepository.existsById(deletedSource.getId())).isFalse();
        assertThat(recurrenceEventRepository.existsById(remainingSource.getId())).isTrue();
    }

    private RecurrenceEvent recurrenceEvent(Account account, Tag tag, String title) {
        return new RecurrenceEvent(
                title,
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
        );
    }
}
