package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.config.GroupInvitationProperties;
import com.calio.calendar.groupinvitation.controller.dto.GroupInvitationListResponse;
import com.calio.calendar.groupinvitation.controller.dto.GroupInvitationSummaryResponse;
import com.calio.calendar.groupinvitation.controller.dto.IssueGroupInvitationResponse;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.repository.GroupInvitationRepository;
import com.calio.calendar.groupspace.domain.GroupMember;
import com.calio.calendar.groupspace.domain.GroupMemberStatus;
import com.calio.calendar.groupspace.repository.GroupMemberRepository;
import com.calio.calendar.groupspace.repository.GroupSpaceRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GroupInvitationService {

    private static final Logger log = LoggerFactory.getLogger(GroupInvitationService.class);
    private static final int MAX_ISSUE_ATTEMPTS = 3;

    private final GroupInvitationRepository invitationRepository;
    private final GroupSpaceRepository groupSpaceRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final InvitationCredentialService credentialService;
    private final GroupInvitationProperties properties;
    private final Clock clock;
    private final TransactionTemplate issueTransaction;

    public GroupInvitationService(
            GroupInvitationRepository invitationRepository,
            GroupSpaceRepository groupSpaceRepository,
            GroupMemberRepository groupMemberRepository,
            InvitationCredentialService credentialService,
            GroupInvitationProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.invitationRepository = invitationRepository;
        this.groupSpaceRepository = groupSpaceRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.credentialService = credentialService;
        this.properties = properties;
        this.clock = clock;
        this.issueTransaction = new TransactionTemplate(transactionManager);
        this.issueTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public IssueGroupInvitationResponse issue(Long accountId, Long groupSpaceId) {
        for (int attempt = 0; attempt < MAX_ISSUE_ATTEMPTS; attempt++) {
            InvitationCredentialPair credentials = credentialService.generatePair();
            try {
                return issueTransaction.execute(
                        status -> issueOnce(accountId, groupSpaceId, credentials)
                );
            } catch (DataIntegrityViolationException exception) {
                // A failed attempt is fully rolled back by TransactionTemplate.
            }
        }

        log.error("Group invitation generation failed. errorCode={}",
                ErrorCode.GROUP_INVITATION_GENERATION_FAILED.name());
        throw new CalioException(ErrorCode.GROUP_INVITATION_GENERATION_FAILED);
    }

    @Transactional(readOnly = true)
    public GroupInvitationListResponse list(Long accountId, Long groupSpaceId) {
        GroupMember member = findActiveMember(groupSpaceId, accountId);
        Instant now = clock.instant();
        var invitations = invitationRepository
                .findActiveSummaries(groupSpaceId, member.getId(), now)
                .stream()
                .map(GroupInvitationSummaryResponse::from)
                .toList();
        return new GroupInvitationListResponse(invitations);
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
                .orElseThrow(GroupInvitationService::invitationNotFound);
        invitationRepository.delete(invitation);
        invitationRepository.flush();
    }

    private IssueGroupInvitationResponse issueOnce(
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

    private void lockGroupSpace(Long groupSpaceId) {
        groupSpaceRepository.findByIdForUpdate(groupSpaceId)
                .orElseThrow(GroupInvitationService::groupSpaceNotFound);
    }

    private GroupMember lockActiveMember(Long groupSpaceId, Long accountId) {
        GroupMember member = groupMemberRepository.findByGroupSpaceIdAndAccountIdForUpdate(
                        groupSpaceId,
                        accountId
                )
                .orElseThrow(GroupInvitationService::groupSpaceNotFound);
        if (member.getStatus() != GroupMemberStatus.ACTIVE) {
            throw groupSpaceNotFound();
        }
        return member;
    }

    private GroupMember findActiveMember(Long groupSpaceId, Long accountId) {
        return groupMemberRepository.findByGroupSpaceIdAndAccountIdAndStatus(
                        groupSpaceId,
                        accountId,
                        GroupMemberStatus.ACTIVE
                )
                .orElseThrow(GroupInvitationService::groupSpaceNotFound);
    }

    private static CalioException groupSpaceNotFound() {
        return new CalioException(ErrorCode.GROUP_SPACE_NOT_FOUND);
    }

    private static CalioException invitationNotFound() {
        return new CalioException(ErrorCode.GROUP_INVITATION_NOT_FOUND);
    }
}
