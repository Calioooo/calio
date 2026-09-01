package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupcalendar.event.domain.GroupCalendarEvent;
import com.calio.calendar.groupcalendar.event.repository.GroupCalendarEventRepository;
import com.calio.calendar.groupcalendar.recurrence.domain.GroupCalendarRecurrenceEvent;
import com.calio.calendar.groupcalendar.recurrence.repository.GroupCalendarRecurrenceEventRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberRole;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import com.calio.calendar.recurrence.domain.RecurrenceSchedule;
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
    private TagRepository tagRepository;

    @BeforeEach
    void setUp() {
        groupCalendarEventRepository.deleteAll();
        groupCalendarRecurrenceEventRepository.deleteAll();
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
        Tag tag = tagRepository.saveAndFlush(Tag.groupDefault(groupSpace));
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
        assertThat(tagRepository.findAll()).isEmpty();
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

    @Test
    @DisplayName("익명 공유 정책은 일정별이 아니라 사용자와 Group Space 조합별로 관리한다")
    void anonymousSharingPolicyIsIndependentForEachGroupMembership() {
        // given
        Account account = accountRepository.saveAndFlush(new Account());
        GroupSpace firstGroup = groupSpaceRepository.saveAndFlush(new GroupSpace(account.getId(), "First", null));
        GroupSpace secondGroup = groupSpaceRepository.saveAndFlush(new GroupSpace(account.getId(), "Second", null));
        GroupMember firstMembership = groupMemberRepository.saveAndFlush(
                new GroupMember(firstGroup, account.getId(), "first", MEMBER_CREATED_AT)
        );
        GroupMember secondMembership = groupMemberRepository.saveAndFlush(
                new GroupMember(secondGroup, account.getId(), "second", MEMBER_CREATED_AT)
        );

        // when
        var response = groupMembershipService.changeAnonymousSharing(account.getId(), firstGroup.getId(), true);

        // then
        assertThat(response.isAnonymous()).isTrue();
        assertThat(groupMemberRepository.findById(firstMembership.getId()).orElseThrow().isAnonymous()).isTrue();
        assertThat(groupMemberRepository.findById(secondMembership.getId()).orElseThrow().isAnonymous()).isFalse();
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

    private record GroupFixture(
            Account ownerAccount,
            Account targetAccount,
            GroupSpace groupSpace,
            GroupMember ownerMember,
            GroupMember targetMember
    ) {
    }
}
