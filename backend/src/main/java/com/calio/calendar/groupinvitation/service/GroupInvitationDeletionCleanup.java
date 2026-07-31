package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupspace.service.GroupSpaceDeletionCleanup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GroupInvitationDeletionCleanup implements GroupSpaceDeletionCleanup {

    private final GroupInvitationRepository groupInvitationRepository;

    public GroupInvitationDeletionCleanup(GroupInvitationRepository groupInvitationRepository) {
        this.groupInvitationRepository = groupInvitationRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteByGroupSpaceId(Long groupSpaceId) {
        var invitations =
                groupInvitationRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpaceId);
        groupInvitationRepository.deleteAllInBatch(invitations);
    }
}
