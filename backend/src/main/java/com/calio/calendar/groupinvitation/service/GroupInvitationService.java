package com.calio.calendar.groupinvitation.service;

import com.calio.calendar.common.error.CalioException;
import com.calio.calendar.common.error.ErrorCode;
import com.calio.calendar.groupinvitation.controller.dto.GroupInvitationListResponse;
import com.calio.calendar.groupinvitation.controller.dto.GroupInvitationSummaryResponse;
import com.calio.calendar.groupinvitation.controller.dto.IssueGroupInvitationResponse;
import com.calio.calendar.groupinvitation.controller.dto.PreviewGroupInvitationRequest;
import com.calio.calendar.groupinvitation.controller.dto.PreviewGroupInvitationResponse;
import com.calio.calendar.groupinvitation.domain.GroupInvitation;
import com.calio.calendar.groupinvitation.service.dto.InvitationCredentialPair;
import com.calio.calendar.groupspace.domain.GroupSpace;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GroupInvitationService {

    private static final Logger log = LoggerFactory.getLogger(GroupInvitationService.class);
    private static final int MAX_ISSUE_ATTEMPTS = 3;
    private static final Set<String> CREDENTIAL_UNIQUE_CONSTRAINTS = Set.of(
            "uk_group_invitations_link_token_hash",
            "uk_group_invitations_invite_code_hash"
    );

    private final InvitationCredentialService credentialService;
    private final GroupInvitationQueryService queryService;
    private final GroupInvitationCommandService commandService;
    private final TransactionTemplate issueTransaction;
    private final Clock clock;

    public GroupInvitationService(
            InvitationCredentialService credentialService,
            GroupInvitationQueryService queryService,
            GroupInvitationCommandService commandService,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.credentialService = credentialService;
        this.queryService = queryService;
        this.commandService = commandService;
        this.clock = clock;
        this.issueTransaction = new TransactionTemplate(transactionManager);
        this.issueTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public GroupInvitationListResponse list(Long accountId, Long groupSpaceId) {
        var invitations = queryService.list(accountId, groupSpaceId)
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
        GroupInvitation invitation = queryService.getInvitationByCredentialHash(
                request.credentialType(),
                credentialHash
        );
        if (invitation.isExpiredAt(clock.instant())) {
            throw new CalioException(ErrorCode.GROUP_INVITATION_EXPIRED);
        }

        GroupSpace groupSpace = queryService.getGroupSpace(invitation.getGroupSpaceId());
        int activeMemberCount = queryService.getActiveMemberCount(groupSpace.getId());
        return PreviewGroupInvitationResponse.from(
                groupSpace,
                activeMemberCount,
                invitation.getExpiresAt()
        );
    }

    public IssueGroupInvitationResponse issue(Long accountId, Long groupSpaceId) {
        for (int attempt = 0; attempt < MAX_ISSUE_ATTEMPTS; attempt++) {
            InvitationCredentialPair credentials = credentialService.generatePair();
            try {
                return issueTransaction.execute(
                        status -> commandService.issueOnce(accountId, groupSpaceId, credentials)
                );
            } catch (DataIntegrityViolationException exception) {
                // A failed attempt is fully rolled back by TransactionTemplate.
                if (!isCredentialCollision(exception)) {
                    throw new CalioException(ErrorCode.GROUP_INVITATION_ISSUE_FAILED, exception);
                }
            }
        }

        log.error("Group invitation generation failed. errorCode={}",
                ErrorCode.GROUP_INVITATION_GENERATION_FAILED.name());
        throw new CalioException(ErrorCode.GROUP_INVITATION_GENERATION_FAILED);
    }

    private boolean isCredentialCollision(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && containsCredentialConstraint(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsCredentialConstraint(String message) {
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        return CREDENTIAL_UNIQUE_CONSTRAINTS.stream().anyMatch(normalizedMessage::contains);
    }
}
