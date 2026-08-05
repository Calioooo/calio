package com.calio.calendar.groupinvitation.service;


import static com.calio.calendar.common.error.ErrorCode.GROUP_INVITATION_NOT_FOUND;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupInvitationQueryService {

    private GroupInvitationRepository groupInvitationRepository;

    @Autowired
    public GroupInvitationQueryService(GroupInvitationRepository groupInvitationRepository) {
        this.groupInvitationRepository = groupInvitationRepository;
    }

    public GroupInvitation getInvitation(InvitationCredentialType credentialType, byte[] credentialHash) {
        return (switch (credentialType) {
            case LINK_TOKEN -> groupInvitationRepository.findByLinkTokenHash(credentialHash);
            case CODE -> groupInvitationRepository.findByInviteCodeHash(credentialHash);
        }).orElseThrow(() -> new CalioException(GROUP_INVITATION_NOT_FOUND));
    }
}
