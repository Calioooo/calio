package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.service.GroupMembershipQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupInvitationQueryService {

    private final GroupInvitationRepository invitationRepository;
    private final GroupMembershipQueryService membershipQueryService;
    private final Clock clock;

    public GroupInvitationQueryService(
            GroupInvitationRepository invitationRepository,
            GroupMembershipQueryService membershipQueryService,
            Clock clock
    ) {
        this.invitationRepository = invitationRepository;
        this.membershipQueryService = membershipQueryService;
        this.clock = clock;
    }

    public List<GroupInvitation> list(Long accountId, Long groupSpaceId) {
        GroupMember member = membershipQueryService.getActiveMembership(groupSpaceId, accountId);
        Instant now = clock.instant();
        return invitationRepository.findActiveInvitations(groupSpaceId, member.getId(), now);
    }

    public GroupInvitation getInvitationByCredentialHash(
            InvitationCredentialType credentialType,
            byte[] credentialHash
    ) {
        return (switch (credentialType) {
            case LINK_TOKEN -> invitationRepository.findByLinkTokenHash(credentialHash);
            case CODE -> invitationRepository.findByInviteCodeHash(credentialHash);
        }).orElseThrow(GroupInvitationQueryService::invitationNotFound);
    }

    public List<GroupInvitation> listExpiredBefore(Instant cutoff, Pageable pageable) {
        return invitationRepository.findCleanupBatch(cutoff, pageable);
    }

    private static CalioException invitationNotFound() {
        return new CalioException(ErrorCode.GROUP_INVITATION_NOT_FOUND);
    }

}
