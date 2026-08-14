package com.calio.calendar.groupspace.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.calio.calendar.groupinvitation.service.GroupInvitationCommandService;
import com.calio.calendar.groupinvitation.service.GroupInvitationQueryService;
import com.calio.calendar.groupinvitation.service.InvitationCredentialService;
import com.calio.calendar.groupspace.controller.dto.AcceptGroupInvitationRequest;
import com.calio.calendar.groupspace.controller.dto.GroupInvitationAcceptCredentialType;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupSpace;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GroupMembershipServiceBoundaryTest {

    @Test
    @DisplayName("초대 수락은 초대 조회와 잠금 명령을 각 초대 서비스에 위임한다")
    void acceptDelegatesInvitationPersistenceToInvitationServices() {
        GroupMembershipQueryService membershipQueryService = mock(GroupMembershipQueryService.class);
        GroupMembershipCommandService membershipCommandService = mock(GroupMembershipCommandService.class);
        GroupSpaceCommandService groupSpaceCommandService = mock(GroupSpaceCommandService.class);
        GroupInvitationQueryService invitationQueryService = mock(GroupInvitationQueryService.class);
        GroupInvitationCommandService invitationCommandService = mock(GroupInvitationCommandService.class);
        InvitationCredentialService credentialService = mock(InvitationCredentialService.class);
        GroupScheduleShareCleanupPort cleanupPort = mock(GroupScheduleShareCleanupPort.class);
        GroupMembershipService service = new GroupMembershipService(
                membershipQueryService,
                membershipCommandService,
                groupSpaceCommandService,
                invitationQueryService,
                invitationCommandService,
                credentialService,
                cleanupPort,
                Clock.fixed(Instant.parse("2026-08-14T00:00:00Z"), ZoneOffset.UTC)
        );
        GroupSpace groupSpace = new GroupSpace(1L, "group", null);
        ReflectionTestUtils.setField(groupSpace, "id", 20L);
        GroupMember issuer = new GroupMember(groupSpace, 1L, "issuer", Instant.parse("2026-08-01T00:00:00Z"));
        ReflectionTestUtils.setField(issuer, "id", 30L);
        GroupInvitation invitation = new GroupInvitation(
                20L,
                30L,
                new byte[32],
                new byte[32],
                Instant.parse("2026-08-15T00:00:00Z")
        );
        byte[] credentialHash = new byte[32];
        when(credentialService.hashValidated(InvitationCredentialType.LINK_TOKEN, "credential"))
                .thenReturn(credentialHash);
        when(invitationQueryService.getInvitationByCredentialHash(InvitationCredentialType.LINK_TOKEN, credentialHash))
                .thenReturn(invitation);
        when(membershipCommandService.lockGroupSpaceForInvitation(20L)).thenReturn(groupSpace);
        when(membershipCommandService.lockMembers(20L)).thenReturn(List.of(issuer));
        when(invitationCommandService.lockInvitation(invitation.getId(), InvitationCredentialType.LINK_TOKEN, credentialHash))
                .thenReturn(invitation);
        when(membershipQueryService.hasActiveNicknameConflict(20L, "joined", null)).thenReturn(false);
        when(membershipCommandService.create(any(), any(), any(), any()))
                .thenAnswer(invocation -> new GroupMember(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3)
                ));

        service.accept(2L, new AcceptGroupInvitationRequest(
                GroupInvitationAcceptCredentialType.LINK_TOKEN,
                "credential",
                "joined"
        ));

        verify(invitationQueryService)
                .getInvitationByCredentialHash(InvitationCredentialType.LINK_TOKEN, credentialHash);
        verify(invitationCommandService)
                .lockInvitation(invitation.getId(), InvitationCredentialType.LINK_TOKEN, credentialHash);
    }
}
