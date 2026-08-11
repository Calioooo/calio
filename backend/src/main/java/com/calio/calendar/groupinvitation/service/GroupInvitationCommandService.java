package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.controller.dto.IssueGroupInvitationResponse;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupinvitation.service.dto.InvitationCredentialPair;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupInvitationCommandService {

    private final GroupInvitationRepository invitationRepository;
    private final InvitationCredentialService credentialService;
    private final GroupInvitationProperties properties;

    public GroupInvitationCommandService(
            GroupInvitationRepository invitationRepository,
            InvitationCredentialService credentialService,
            GroupInvitationProperties properties
    ) {
        this.invitationRepository = invitationRepository;
        this.credentialService = credentialService;
        this.properties = properties;
    }

    public IssueGroupInvitationResponse create(
            Long groupSpaceId,
            Long createdByMemberId,
            InvitationCredentialPair credentials,
            Instant expiresAt
    ) {
        GroupInvitation invitation = invitationRepository.saveAndFlush(
                new GroupInvitation(
                        groupSpaceId,
                        createdByMemberId,
                        credentials.linkTokenHash(),
                        credentials.inviteCodeHash(),
                        expiresAt
                )
        );
        return IssueGroupInvitationResponse.from(
                invitation,
                credentialService.inviteUrl(credentials.linkToken()),
                credentials.inviteCode()
        );
    }

    public void delete(GroupInvitation invitation) {
        invitationRepository.delete(invitation);
        invitationRepository.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteExpiredBatch(Instant cutoff) {
        List<GroupInvitation> invitations = invitationRepository.findCleanupBatch(
                cutoff,
                PageRequest.of(0, properties.getCleanupBatchSize())
        );
        invitationRepository.deleteAllInBatch(invitations);
        return invitations.size();
    }

    public void deleteAllByGroupSpaceId(Long groupSpaceId) {
        List<GroupInvitation> invitations =
                invitationRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpaceId);
        invitationRepository.deleteAllInBatch(invitations);
    }

}
