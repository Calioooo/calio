package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.domain.InvitationCredentialType;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupinvitation.service.dto.InvitationCredentialPair;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.domain.GroupSpace;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupInvitationCommandService {

    private final GroupInvitationRepository invitationRepository;
    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;

    public GroupInvitationCommandService(
            GroupInvitationRepository invitationRepository,
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository
    ) {
        this.invitationRepository = invitationRepository;
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    public GroupInvitation lockInvitation(
            Long invitationId,
            InvitationCredentialType credentialType,
            byte[] credentialHash
    ) {
        return invitationRepository.findByIdAndCredentialHashForUpdate(
                        invitationId,
                        credentialType.name(),
                        credentialHash
                )
                .orElseThrow(GroupInvitationCommandService::invitationNotFound);
    }

    public List<GroupInvitation> lockInvitationsCreatedBy(Long memberId) {
        return invitationRepository.findAllByCreatedByMemberIdForUpdateOrderById(memberId);
    }

    public GroupSpace lockGroupSpace(Long groupSpaceId) {
        return groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(GroupInvitationCommandService::groupSpaceNotFound);
    }

    public GroupMember lockActiveMember(Long groupSpaceId, Long accountId) {
        GroupMember member = groupMemberRepository.findByGroupSpaceIdAndAccountIdForUpdate(
                        groupSpaceId,
                        accountId
                )
                .orElseThrow(GroupInvitationCommandService::groupSpaceNotFound);
        if (member.getStatus() != GroupMemberStatus.ACTIVE) {
            throw groupSpaceNotFound();
        }
        return member;
    }

    public Optional<GroupInvitation> findRevocableInvitationForUpdate(
            Long groupSpaceId,
            Long invitationId,
            Long createdByMemberId,
            Instant now
    ) {
        return invitationRepository.findScopedForUpdate(
                groupSpaceId,
                invitationId,
                createdByMemberId,
                now
        );
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

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }

    private static CalioException invitationNotFound() {
        return new CalioException(ErrorCode.GROUP_INVITATION_NOT_FOUND);
    }

}
