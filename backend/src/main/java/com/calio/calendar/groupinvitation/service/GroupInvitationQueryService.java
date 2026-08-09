package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.controller.dto.GroupInvitationListResponse;
import com.calio.calendar.groupinvitation.controller.dto.GroupInvitationSummaryResponse;
import com.calio.calendar.groupinvitation.controller.dto.PreviewGroupInvitationRequest;
import com.calio.calendar.groupinvitation.controller.dto.PreviewGroupInvitationResponse;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupInvitationQueryService {

    private final GroupInvitationRepository invitationRepository;
    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final InvitationCredentialService credentialService;
    private final Clock clock;

    public GroupInvitationQueryService(
            GroupInvitationRepository invitationRepository,
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository,
            InvitationCredentialService credentialService,
            Clock clock
    ) {
        this.invitationRepository = invitationRepository;
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.credentialService = credentialService;
        this.clock = clock;
    }

    public GroupInvitationListResponse list(Long accountId, Long groupSpaceId) {
        GroupMember member = findActiveMember(groupSpaceId, accountId);
        Instant now = clock.instant();
        var invitations = invitationRepository
                .findActiveInvitations(groupSpaceId, member.getId(), now)
                .stream()
                .map(GroupInvitationSummaryResponse::from)
                .toList();
        return new GroupInvitationListResponse(invitations);
    }

    public PreviewGroupInvitationResponse preview(PreviewGroupInvitationRequest request) {
        byte[] credentialHash = credentialService.hashValidated(
                request.credentialType(),
                request.credential()
        );
        GroupInvitation invitation = (switch (request.credentialType()) {
            case LINK_TOKEN -> invitationRepository.findByLinkTokenHash(credentialHash);
            case CODE -> invitationRepository.findByInviteCodeHash(credentialHash);
        }).orElseThrow(GroupInvitationQueryService::invitationNotFound);

        if (invitation.isExpiredAt(clock.instant())) {
            throw new CalioException(ErrorCode.GROUP_INVITATION_EXPIRED);
        }

        GroupSpace groupSpace = groupSpaceRepository.findById(invitation.getGroupSpaceId())
                .orElseThrow(GroupInvitationQueryService::invitationNotFound);
        int activeMemberCount = groupMemberRepository.countByGroupSpace_IdAndStatus(
                groupSpace.getId(),
                GroupMemberStatus.ACTIVE
        );
        return PreviewGroupInvitationResponse.from(
                groupSpace,
                activeMemberCount,
                invitation.getExpiresAt()
        );
    }

    private GroupMember findActiveMember(Long groupSpaceId, Long accountId) {
        return groupMemberRepository.findByGroupSpaceIdAndAccountIdAndStatus(
                        groupSpaceId,
                        accountId,
                        GroupMemberStatus.ACTIVE
                )
                .orElseThrow(GroupInvitationQueryService::groupSpaceNotFound);
    }

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }

    private static CalioException invitationNotFound() {
        return new CalioException(ErrorCode.GROUP_INVITATION_NOT_FOUND);
    }
}
