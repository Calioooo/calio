package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.controller.dto.IssueGroupInvitationResponse;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupinvitation.service.dto.InvitationCredentialPair;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupInvitationCommandService {

    private final GroupInvitationRepository invitationRepository;
    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final InvitationCredentialService credentialService;
    private final GroupInvitationProperties properties;
    private final Clock clock;

    public GroupInvitationCommandService(
            GroupInvitationRepository invitationRepository,
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository,
            InvitationCredentialService credentialService,
            GroupInvitationProperties properties,
            Clock clock
    ) {
        this.invitationRepository = invitationRepository;
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.credentialService = credentialService;
        this.properties = properties;
        this.clock = clock;
    }

    IssueGroupInvitationResponse issueOnce(
            Long accountId,
            Long groupSpaceId,
            InvitationCredentialPair credentials
    ) {
        lockGroupSpace(groupSpaceId);
        GroupMember issuer = lockActiveMember(groupSpaceId, accountId);
        Instant expiresAt = clock.instant().plus(properties.getTtl());
        GroupInvitation invitation = invitationRepository.saveAndFlush(
                new GroupInvitation(
                        groupSpaceId,
                        issuer.getId(),
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

    @Transactional
    public void revoke(Long accountId, Long groupSpaceId, Long invitationId) {
        lockGroupSpace(groupSpaceId);
        GroupMember member = lockActiveMember(groupSpaceId, accountId);
        GroupInvitation invitation = invitationRepository
                .findScopedForUpdate(
                        groupSpaceId,
                        invitationId,
                        member.getId(),
                        clock.instant()
                )
                .orElseThrow(GroupInvitationCommandService::invitationNotFound);
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

    @Transactional
    public void deleteAllByGroupSpaceId(Long groupSpaceId) {
        List<GroupInvitation> invitations =
                invitationRepository.findAllByGroupSpaceIdForUpdateOrderById(groupSpaceId);
        invitationRepository.deleteAllInBatch(invitations);
    }

    private void lockGroupSpace(Long groupSpaceId) {
        groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(GroupInvitationCommandService::groupSpaceNotFound);
    }

    private GroupMember lockActiveMember(Long groupSpaceId, Long accountId) {
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

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }

    private static CalioException invitationNotFound() {
        return new CalioException(ErrorCode.GROUP_INVITATION_NOT_FOUND);
    }
}
