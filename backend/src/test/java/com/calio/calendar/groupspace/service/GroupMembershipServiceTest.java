package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.event.domain.Event;
import com.calio.calendar.event.repository.EventRepository;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import com.calio.calendar.groupcalendar.sharing.event.domain.PersonalEventGroupShare;
import com.calio.calendar.groupcalendar.sharing.event.repository.PersonalEventGroupShareRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShare;
import com.calio.calendar.groupcalendar.sharing.recurrence.domain.PersonalRecurrenceGroupShareScope;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareOccurrenceOverrideRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareRepository;
import com.calio.calendar.groupcalendar.sharing.recurrence.repository.PersonalRecurrenceGroupShareSelectedOriginRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberRole;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
import com.calio.calendar.recurrence.domain.RecurrenceEvent;
import com.calio.calendar.recurrence.repository.RecurrenceEventRepository;
import com.calio.calendar.tag.domain.Tag;
import com.calio.calendar.tag.domain.TagType;
import com.calio.calendar.tag.repository.TagRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-membership-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GroupMembershipServiceTest {

    private static final Instant MEMBER_CREATED_AT = Instant.parse("2026-07-30T00:00:00Z");

    @Autowired
    private GroupMembershipService groupMembershipService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private GroupSpaceRepository groupSpaceRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    private GroupCalendarEventRepository groupCalendarEventRepository;

    @Autowired
    private GroupCalendarRecurrenceEventRepository groupCalendarRecurrenceEventRepository;

    @Autowired
    private EventRepository eventRepository;

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

    @BeforeEach
    void setUp() {
        occurrenceOverrideRepository.deleteAll();
        selectedOriginRepository.deleteAll();
        personalRecurrenceGroupShareRepository.deleteAll();
        personalEventGroupShareRepository.deleteAll();
        groupCalendarEventRepository.deleteAll();
        groupCalendarRecurrenceEventRepository.deleteAll();
        eventRepository.deleteAll();
        recurrenceEventRepository.deleteAll();
        tagRepository.deleteAll();
        groupInvitationRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupSpaceRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("OWNER 이전은 이전 owner를 MEMBER로, 대상 ACTIVE member를 OWNER로 projection한다")
    void transferOwnershipUpdatesOwnerAndResponseRoles() {
        // given
        GroupFixture fixture = createFixture();

        // when
        var response = groupMembershipService.transferOwnership(
                fixture.ownerAccount().getId(),
                fixture.groupSpace().getId(),
                fixture.targetMember().getId()
        );

        // then
        assertThat(response.previousOwner().role()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(response.owner().role()).isEqualTo(GroupMemberRole.OWNER);
        assertThat(groupSpaceRepository.findById(fixture.groupSpace().getId()).orElseThrow()
                .getOwnerAccountId()).isEqualTo(fixture.targetAccount().getId());
    }

    @Test
    @DisplayName("OWNER는 자기 자신에게 소유권을 이전할 수 없다")
    void transferOwnershipRejectsSelfTarget() {
        // given
        GroupFixture fixture = createFixture();

        // when, then
        assertThatThrownBy(() -> groupMembershipService.transferOwnership(
                fixture.ownerAccount().getId(),
                fixture.groupSpace().getId(),
                fixture.ownerMember().getId()
        )).isInstanceOf(CalioException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_OWNER_TRANSFER_INVALID);
    }

    @Test
    @DisplayName("OWNER 이전 대상이 inactive member이면 GROUP_MEMBER_NOT_FOUND를 반환한다")
    void transferOwnershipRejectsInactiveTarget() {
        // given
        GroupFixture fixture = createFixture();
        fixture.targetMember().deactivate(GroupMemberStatus.LEFT, Instant.now());
        groupMemberRepository.saveAndFlush(fixture.targetMember());

        // when, then
        assertThatThrownBy(() -> groupMembershipService.transferOwnership(
                fixture.ownerAccount().getId(),
                fixture.groupSpace().getId(),
                fixture.targetMember().getId()
        )).isInstanceOf(CalioException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("마지막 owner 탈퇴는 Group Space 태그와 직접·반복 일정을 함께 hard-delete한다")
    void givenSoleOwnerGroup_whenLeave_thenDeletesTagsAndCalendarData() {
        // given
        Account owner = accountRepository.saveAndFlush(new Account());
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(new GroupSpace(owner.getId(), "Shared", null));
        groupMemberRepository.saveAndFlush(new GroupMember(groupSpace, owner.getId(), "owner", MEMBER_CREATED_AT));
        Tag tag = tagRepository.saveAndFlush(new Tag(TagType.GROUP_DEFAULT, "기타", "#64748B", groupSpace));
        Tag personalTag = tagRepository.saveAndFlush(
                new Tag(TagType.PERSONAL_DEFAULT, "개인 기타", "#64748B")
        );
        Event sourceEvent = eventRepository.saveAndFlush(personalEvent(owner, personalTag));
        RecurrenceEvent sourceRecurrence = recurrenceEventRepository.saveAndFlush(
                personalRecurrence(owner, personalTag)
        );
        personalEventGroupShareRepository.saveAndFlush(
                new PersonalEventGroupShare(sourceEvent, groupSpace)
        );
        personalRecurrenceGroupShareRepository.saveAndFlush(new PersonalRecurrenceGroupShare(
                sourceRecurrence,
                groupSpace,
                PersonalRecurrenceGroupShareScope.WHOLE_SERIES
        ));
        groupCalendarEventRepository.saveAndFlush(new GroupCalendarEvent(
                groupSpace,
                owner,
                tag,
                "직접 일정",
                null,
                MEMBER_CREATED_AT,
                MEMBER_CREATED_AT.plusSeconds(3600),
                false,
                "UTC"
        ));
        groupCalendarRecurrenceEventRepository.saveAndFlush(new GroupCalendarRecurrenceEvent(
                groupSpace,
                owner,
                tag,
                "반복 일정",
                null,
                new RecurrenceSchedule(
                        MEMBER_CREATED_AT,
                        MEMBER_CREATED_AT.plusSeconds(3600),
                        false,
                        "UTC"
                ),
                java.util.List.of("RRULE:FREQ=DAILY")
        ));

        // when
        groupMembershipService.leave(owner.getId(), groupSpace.getId());

        // then
        assertThat(groupCalendarEventRepository.findAll()).isEmpty();
        assertThat(groupCalendarRecurrenceEventRepository.findAll()).isEmpty();
        assertThat(personalEventGroupShareRepository.findAllByEventId(sourceEvent.getId())).isEmpty();
        assertThat(personalRecurrenceGroupShareRepository.findAllByRecurrenceEventId(sourceRecurrence.getId()))
                .isEmpty();
        assertThat(tagRepository.findByGroupSpace_Id(groupSpace.getId())).isEmpty();
        assertThat(tagRepository.findById(personalTag.getId())).isPresent();
    }

    @Test
    @DisplayName("멤버 탈퇴·강퇴는 해당 Group Space의 개인 share mapping만 hard-delete하고 원본은 유지한다")
    void givenMemberSharedPersonalSchedules_whenKicked_thenDeletesOnlyMemberMappings() {
        // given
        GroupFixture fixture = createFixture();
        Tag tag = tagRepository.saveAndFlush(new Tag(TagType.PERSONAL_DEFAULT, "기타", "#64748B"));
        Event removedEvent = eventRepository.saveAndFlush(personalEvent(fixture.targetAccount(), tag));
        Event ownerEvent = eventRepository.saveAndFlush(personalEvent(fixture.ownerAccount(), tag));
        RecurrenceEvent removedRecurrence = recurrenceEventRepository.saveAndFlush(
                personalRecurrence(fixture.targetAccount(), tag)
        );
        RecurrenceEvent ownerRecurrence = recurrenceEventRepository.saveAndFlush(
                personalRecurrence(fixture.ownerAccount(), tag)
        );
        personalEventGroupShareRepository.saveAndFlush(
                new PersonalEventGroupShare(removedEvent, fixture.groupSpace())
        );
        PersonalEventGroupShare ownerEventShare = personalEventGroupShareRepository.saveAndFlush(
                new PersonalEventGroupShare(ownerEvent, fixture.groupSpace())
        );
        personalRecurrenceGroupShareRepository.saveAndFlush(new PersonalRecurrenceGroupShare(
                removedRecurrence,
                fixture.groupSpace(),
                PersonalRecurrenceGroupShareScope.WHOLE_SERIES
        ));
        PersonalRecurrenceGroupShare ownerRecurrenceShare = personalRecurrenceGroupShareRepository.saveAndFlush(
                new PersonalRecurrenceGroupShare(
                        ownerRecurrence,
                        fixture.groupSpace(),
                        PersonalRecurrenceGroupShareScope.WHOLE_SERIES
                )
        );

        // when
        groupMembershipService.kick(
                fixture.ownerAccount().getId(),
                fixture.groupSpace().getId(),
                fixture.targetMember().getId()
        );

        // then
        assertThat(personalEventGroupShareRepository.findAllByEventId(removedEvent.getId())).isEmpty();
        assertThat(personalRecurrenceGroupShareRepository.findAllByRecurrenceEventId(removedRecurrence.getId()))
                .isEmpty();
        assertThat(personalEventGroupShareRepository.findAllByEventId(ownerEvent.getId()))
                .extracting(PersonalEventGroupShare::getId)
                .containsExactly(ownerEventShare.getId());
        assertThat(personalRecurrenceGroupShareRepository.findAllByRecurrenceEventId(ownerRecurrence.getId()))
                .extracting(PersonalRecurrenceGroupShare::getId)
                .containsExactly(ownerRecurrenceShare.getId());
        assertThat(eventRepository.existsById(removedEvent.getId())).isTrue();
        assertThat(recurrenceEventRepository.existsById(removedRecurrence.getId())).isTrue();
    }

    @Test
    @DisplayName("OWNER 이전 대상이 Group Space에 없으면 GROUP_MEMBER_NOT_FOUND를 반환한다")
    void transferOwnershipRejectsMissingTarget() {
        // given
        GroupFixture fixture = createFixture();

        // when, then
        assertThatThrownBy(() -> groupMembershipService.transferOwnership(
                fixture.ownerAccount().getId(),
                fixture.groupSpace().getId(),
                Long.MAX_VALUE
        )).isInstanceOf(CalioException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GROUP_MEMBER_NOT_FOUND);
    }

    private GroupFixture createFixture() {
        Account ownerAccount = accountRepository.saveAndFlush(new Account());
        Account targetAccount = accountRepository.saveAndFlush(new Account());
        GroupSpace groupSpace = groupSpaceRepository.saveAndFlush(
                new GroupSpace(ownerAccount.getId(), "Shared", null)
        );
        GroupMember ownerMember = groupMemberRepository.saveAndFlush(
                new GroupMember(groupSpace, ownerAccount.getId(), "owner", MEMBER_CREATED_AT)
        );
        GroupMember targetMember = groupMemberRepository.saveAndFlush(
                new GroupMember(groupSpace, targetAccount.getId(), "target", MEMBER_CREATED_AT)
        );
        return new GroupFixture(ownerAccount, targetAccount, groupSpace, ownerMember, targetMember);
    }

    private Event personalEvent(Account account, Tag tag) {
        return new Event(
                "개인 일정",
                null,
                MEMBER_CREATED_AT,
                MEMBER_CREATED_AT.plusSeconds(3600),
                false,
                "UTC",
                null,
                tag,
                account
        );
    }

    private RecurrenceEvent personalRecurrence(Account account, Tag tag) {
        return new RecurrenceEvent(
                "개인 반복 일정",
                null,
                new RecurrenceSchedule(
                        MEMBER_CREATED_AT,
                        MEMBER_CREATED_AT.plusSeconds(3600),
                        false,
                        "UTC"
                ),
                java.util.List.of("RRULE:FREQ=DAILY"),
                tag,
                account
        );
    }

    private record GroupFixture(
            Account ownerAccount,
            Account targetAccount,
            GroupSpace groupSpace,
            GroupMember ownerMember,
            GroupMember targetMember
    ) {
    }
}
