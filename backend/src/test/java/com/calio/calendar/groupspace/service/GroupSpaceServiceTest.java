package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupinvitation.service.GroupInvitationCommandService;
import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.groupcalendar.sharing.event.repository.PersonalEventGroupShareRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareScope;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareOccurrenceOverrideRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareSelectedOriginRepository;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-space-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(GroupSpaceServiceTest.InvitationServiceTestConfig.class)
class GroupSpaceServiceTest {

    @Autowired
    private GroupSpaceService groupSpaceService;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private GroupCalendarEventRepository groupCalendarEventRepository;

    @Autowired
    private GroupInvitationCommandService invitationCommandService;

    @Autowired
    private PersonalEventGroupShareRepository personalEventGroupShareRepository;

    @Autowired
    private PersonalRecurrenceGroupShareOccurrenceOverrideRepository occurrenceOverrideRepository;

    @Autowired
    private PersonalRecurrenceGroupShareRepository personalRecurrenceGroupShareRepository;

    @Autowired
    private PersonalRecurrenceGroupShareSelectedOriginRepository selectedOriginRepository;

    @Autowired
    private RecurrenceEventRepository recurrenceEventRepository;

    @Autowired
    private TagRepository tagRepository;

    private Account account;

    @BeforeEach
    void setUp() {
        reset(invitationCommandService);
        occurrenceOverrideRepository.deleteAll();
        selectedOriginRepository.deleteAll();
        personalRecurrenceGroupShareRepository.deleteAll();
        personalEventGroupShareRepository.deleteAll();
        groupCalendarEventRepository.deleteAll();
        eventRepository.deleteAll();
        recurrenceEventRepository.deleteAll();
        tagRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupSpaceRepository.deleteAll();
        account = accountRepository.saveAndFlush(new Account());
    }

    @Test
    @DisplayName("하나의 Account는 여러 Group Space의 OWNER가 될 수 있다")
    void sameAccountCanOwnMultipleGroupSpaces() {
        // when
        var first = groupSpaceService.create(
                account.getId(),
                new CreateGroupSpaceRequest("First", null, "first")
        );
        var second = groupSpaceService.create(
                account.getId(),
                new CreateGroupSpaceRequest("Second", null, "second")
        );

        // then
        assertThat(first.groupSpaceId()).isNotEqualTo(second.groupSpaceId());
        assertThat(groupSpaceRepository.findAll())
                .allMatch(groupSpace -> groupSpace.getOwnerAccountId().equals(account.getId()));
        assertThat(groupMemberRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("dependent cleanup 실패는 membership과 Group Space 삭제 전체를 rollback한다")
    void cleanupFailureRollsBackDelete() {
        // given
        var created = groupSpaceService.create(
                account.getId(),
                new CreateGroupSpaceRequest("Rollback", null, "owner")
        );
        doThrow(new IllegalStateException("simulated cleanup failure"))
                .when(invitationCommandService)
                .deleteAllByGroupSpaceId(created.groupSpaceId());

        // when, then
        assertThatThrownBy(() -> groupSpaceService.delete(account.getId(), created.groupSpaceId()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(groupSpaceRepository.existsById(created.groupSpaceId())).isTrue();
        assertThat(groupMemberRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("OWNER delete는 membership을 먼저 삭제하고 Group Space를 hard-delete한다")
    void ownerDeleteRemovesMembershipAndGroupSpace() {
        // given
        var created = groupSpaceService.create(
                account.getId(),
                new CreateGroupSpaceRequest("Delete", null, "owner")
        );

        // when
        groupSpaceService.delete(account.getId(), created.groupSpaceId());

        // then
        assertThat(groupMemberRepository.count()).isZero();
        assertThat(groupSpaceRepository.existsById(created.groupSpaceId())).isFalse();
    }

    @Test
    @DisplayName("Group Space 삭제는 모든 share mapping과 직접 일정을 태그보다 먼저 hard-delete한다")
    void givenGroupWithSharedAndDirectSchedules_whenDelete_thenCleansDependentState() {
        // given
        var created = groupSpaceService.create(
                account.getId(),
                new CreateGroupSpaceRequest("Delete shares", null, "owner")
        );
        GroupSpace groupSpace = groupSpaceRepository.findById(created.groupSpaceId()).orElseThrow();
        Tag groupTag = tagRepository.findByGroupSpace_IdOrderByIdAsc(groupSpace.getId()).getFirst();
        Tag personalTag = tagRepository.saveAndFlush(
                new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B")
        );
        Event sourceEvent = eventRepository.saveAndFlush(new Event(
                "개인 일정",
                null,
                Instant.parse("2028-01-01T09:00:00Z"),
                Instant.parse("2028-01-01T10:00:00Z"),
                false,
                "UTC",
                null,
                personalTag,
                account
        ));
        RecurrenceEvent sourceRecurrence = recurrenceEventRepository.saveAndFlush(new RecurrenceEvent(
                "개인 반복 일정",
                null,
                new RecurrenceSchedule(
                        Instant.parse("2028-01-01T09:00:00Z"),
                        Instant.parse("2028-01-01T10:00:00Z"),
                        false,
                        "UTC"
                ),
                List.of("RRULE:FREQ=WEEKLY"),
                personalTag,
                account
        ));
        personalEventGroupShareRepository.saveAndFlush(new PersonalEventGroupShare(sourceEvent, groupSpace));
        personalRecurrenceGroupShareRepository.saveAndFlush(new PersonalRecurrenceGroupShare(
                sourceRecurrence,
                groupSpace,
                PersonalRecurrenceGroupShareScope.WHOLE_SERIES
        ));
        groupCalendarEventRepository.saveAndFlush(new GroupCalendarEvent(
                groupSpace,
                account,
                groupTag,
                "직접 일정",
                null,
                Instant.parse("2028-01-01T11:00:00Z"),
                Instant.parse("2028-01-01T12:00:00Z"),
                false,
                "UTC"
        ));

        // when
        groupSpaceService.delete(account.getId(), groupSpace.getId());

        // then
        assertThat(personalEventGroupShareRepository.findAllByEventId(sourceEvent.getId())).isEmpty();
        assertThat(personalRecurrenceGroupShareRepository.findAllByRecurrenceEventId(sourceRecurrence.getId()))
                .isEmpty();
        assertThat(groupCalendarEventRepository.findAll()).isEmpty();
        assertThat(tagRepository.findByGroupSpace_Id(groupSpace.getId())).isEmpty();
        assertThat(groupSpaceRepository.existsById(groupSpace.getId())).isFalse();
        assertThat(eventRepository.existsById(sourceEvent.getId())).isTrue();
        assertThat(recurrenceEventRepository.existsById(sourceRecurrence.getId())).isTrue();
    }

    @TestConfiguration
    static class InvitationServiceTestConfig {

        @Bean
        @Primary
        GroupInvitationCommandService groupInvitationCommandService() {
            return mock(GroupInvitationCommandService.class);
        }
    }
}
