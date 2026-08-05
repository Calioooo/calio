package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.controller.dto.PreviewGroupInvitationRequest;
import com.calio.calendar.groupinvitation.controller.dto.PreviewGroupInvitationResponse;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Clock;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupInvitationPreviewService {

    private final GroupInvitationQueryService invitationQueryService;
    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final InvitationCredentialService credentialService;
    private final Clock clock;

    public GroupInvitationPreviewService(
            GroupInvitationQueryService invitationQueryService,
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository,
            InvitationCredentialService credentialService,
            Clock clock
    ) {
        this.invitationQueryService = invitationQueryService;
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.credentialService = credentialService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PreviewGroupInvitationResponse preview(PreviewGroupInvitationRequest request) {
        byte[] credentialHash = credentialService.hashValidated(
                request.credentialType(),
                request.credential()
        );
        GroupInvitation invitation = invitationQueryService.getInvitation(request.credentialType(), credentialHash);

        if (invitation.isExpiredAt(clock.instant())) {
            throw new CalioException(ErrorCode.GROUP_INVITATION_EXPIRED);
        }

        GroupSpace groupSpace = groupSpaceRepository.findById(invitation.getGroupSpaceId())
                .orElseThrow(GroupInvitationPreviewService::invitationNotFound);
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

    private static CalioException invitationNotFound() {
        return new CalioException(ErrorCode.GROUP_INVITATION_NOT_FOUND);
    }
}
