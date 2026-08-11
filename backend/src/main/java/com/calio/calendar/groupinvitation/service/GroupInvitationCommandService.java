package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupinvitation.service.dto.InvitationCredentialPair;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupInvitationCommandService {

    private final GroupInvitationRepository invitationRepository;

    public GroupInvitationCommandService(GroupInvitationRepository invitationRepository) {
        this.invitationRepository = invitationRepository;
    }

    public GroupInvitation create(
            Long groupSpaceId,
            Long createdByMemberId,
            InvitationCredentialPair credentials,
            Instant expiresAt
    ) {
        return invitationRepository.saveAndFlush(
                new GroupInvitation(
                        groupSpaceId,
                        createdByMemberId,
                        credentials.linkTokenHash(),
                        credentials.inviteCodeHash(),
                        expiresAt
                )
        );
    }

    public void delete(GroupInvitation invitation) {
        invitationRepository.delete(invitation);
        invitationRepository.flush();
    }

    public int delete(List<GroupInvitation> invitations) {
        invitationRepository.deleteAllInBatch(invitations);
        return invitations.size();
    }

    public void deleteAllByGroupSpaceId(Long groupSpaceId) {
        List<GroupInvitation> invitations =
                invitationRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpaceId);
        invitationRepository.deleteAllInBatch(invitations);
    }

}
