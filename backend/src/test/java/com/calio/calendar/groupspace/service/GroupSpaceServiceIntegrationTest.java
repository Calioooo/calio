package com.calio.calendar.groupspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.calio.calendar.account.domain.Account;
import com.calio.calendar.account.repository.AccountRepository;
import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationRequest;
import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationResponse;
import com.calio.calendar.groupspace.controller.dto.CreateGroupInvitationResponse;
import com.calio.calendar.groupspace.controller.dto.CreateGroupSpaceRequest;
import com.calio.calendar.groupspace.controller.dto.GroupMemberListResponse;
import com.calio.calendar.groupspace.controller.dto.GroupOwnerDelegateDto;
import com.calio.calendar.groupspace.controller.dto.GroupSpaceResponseDto;
import com.calio.calendar.groupspace.domain.GroupJoinResult;
import com.calio.calendar.groupspace.domain.GroupMemberRole;
import com.calio.calendar.groupspace.domain.InvitationCredentialType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:group-space-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GroupSpaceServiceIntegrationTest {

    @Autowired
    private GroupSpaceService groupSpaceService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("multi-use invitation 가입은 JOINED 후 ALREADY_MEMBER로 수렴하고 OWNER 위임 뒤 이전 OWNER는 탈퇴할 수 있다")
    void invitationAcceptTransferAndLeaveFollowLifecycleContract() {
        Account ownerAccount = accountRepository.saveAndFlush(new Account());
        Account memberAccount = accountRepository.saveAndFlush(new Account());
        GroupSpaceResponseDto created = groupSpaceService.createGroup(
                ownerAccount.getId(),
                new CreateGroupSpaceRequest("Team", "🙂", "Owner")
        );
        CreateGroupInvitationResponse invitation =
                groupSpaceService.createInvitation(ownerAccount.getId(), created.id());

        AcceptGroupInvitationResponse joined = groupSpaceService.acceptInvitation(
                memberAccount.getId(),
                acceptRequest(invitation, "Member")
        );
        AcceptGroupInvitationResponse alreadyMember = groupSpaceService.acceptInvitation(
                memberAccount.getId(),
                acceptRequest(invitation, "Changed")
        );

        assertThat(joined.joinResult()).isEqualTo(GroupJoinResult.JOINED);
        assertThat(joined.membership()).isEqualTo(joined.groupSpace().myMembership());
        assertThat(joined.groupSpace().memberCount()).isEqualTo(2);
        assertThat(alreadyMember.joinResult()).isEqualTo(GroupJoinResult.ALREADY_MEMBER);
        assertThat(alreadyMember.membership().nickname()).isEqualTo("Member");

        GroupOwnerDelegateDto delegated = groupSpaceService.transferOwner(
                ownerAccount.getId(),
                created.id(),
                joined.membership().memberId()
        );
        groupSpaceService.leaveGroup(ownerAccount.getId(), created.id());
        GroupMemberListResponse remaining = groupSpaceService.listMembers(memberAccount.getId(), created.id());

        assertThat(delegated.previousOwner().role()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(delegated.owner().role()).isEqualTo(GroupMemberRole.OWNER);
        assertThat(remaining.members()).singleElement()
                .satisfies(member -> assertThat(member.role()).isEqualTo(GroupMemberRole.OWNER));
    }

    @Test
    @DisplayName("탈퇴 전에 생성된 invitation은 같은 membership row를 재활성화할 수 없다")
    void invitationCreatedBeforeLeavingCannotRejoinMembership() {
        Account ownerAccount = accountRepository.saveAndFlush(new Account());
        Account memberAccount = accountRepository.saveAndFlush(new Account());
        GroupSpaceResponseDto created = groupSpaceService.createGroup(
                ownerAccount.getId(),
                new CreateGroupSpaceRequest("Rejoin", null, "Owner")
        );
        CreateGroupInvitationResponse staleInvitation =
                groupSpaceService.createInvitation(ownerAccount.getId(), created.id());
        groupSpaceService.acceptInvitation(
                memberAccount.getId(),
                acceptRequest(staleInvitation, "Member")
        );
        groupSpaceService.leaveGroup(memberAccount.getId(), created.id());

        assertThatThrownBy(() -> groupSpaceService.acceptInvitation(
                memberAccount.getId(),
                acceptRequest(staleInvitation, "Member2")
        )).isInstanceOfSatisfying(CalioException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.GROUP_MEMBER_REJOIN_INVITATION_REQUIRED)
        );
    }

    private AcceptGroupInvitationRequest acceptRequest(
            CreateGroupInvitationResponse invitation,
            String nickname
    ) {
        return new AcceptGroupInvitationRequest(
                InvitationCredentialType.CODE,
                invitation.inviteCode(),
                nickname
        );
    }
}
